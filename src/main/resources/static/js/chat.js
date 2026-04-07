/* ============================================
   Expense Tracker - Chat Panel (mobile + desktop)
   ============================================ */

let chatExpenseMap = {};
let _chatOffset = 0;
const _chatPageSize = 10;
let _chatHasMore = false;
let _chatLoadingOlder = false;

function renderChatPanel(container) {
    const panel = container || document.getElementById('panel-chat');
    panel.innerHTML = `
    <div class="chat-container">
        <div class="chat-header">
            <i class="fa-solid fa-robot"></i>
            <span class="chat-header-title">Expense Assistant</span>
        </div>
        <div class="chat-load-older" id="chatLoadOlder" style="display:none">
            <button class="btn btn-outline btn-sm" onclick="loadOlderMessages()">
                <i class="fa-solid fa-chevron-up"></i> Load earlier messages
            </button>
        </div>
        <div class="chat-messages" id="chatMessages">
            <div class="chat-bubble bot">
                <p>Hi! I can help you log expenses quickly. Just type something like:</p>
                <p style="font-style:italic; margin-top:0.5rem; color:var(--text-light);">"lunch 12.50 SGD"<br>"coffee 5, taxi 15 USD"<br>"yesterday dinner 45 EUR at Italian restaurant"</p>
            </div>
        </div>
        <div class="chat-input-area">
            <textarea class="chat-input" id="chatInput" placeholder="Type expenses here..." rows="1" onkeydown="chatKeydown(event)"></textarea>
            <button class="btn btn-primary chat-send-btn" onclick="sendChat()">
                <i class="fa-solid fa-paper-plane"></i>
            </button>
        </div>
    </div>`;
    _chatOffset = 0;
    _chatHasMore = false;
    chatExpenseMap = {};
    loadChatHistory(true);
}

async function loadChatHistory(initial = false) {
    const data = await api(`/api/chat/history?limit=${_chatPageSize}&offset=0`);
    if (!data || !data.messages) return;
    Object.assign(chatExpenseMap, data.expenses || {});
    _chatHasMore = data.hasMore || false;
    _chatOffset = data.messages.length;

    const container = document.getElementById('chatMessages');
    if (!container) return;
    for (const msg of data.messages) {
        appendChatBubble(msg.role === 'USER' ? 'user' : 'bot', msg.text, msg.linkedExpenseIds, msg.createdAt);
    }
    container.scrollTop = container.scrollHeight;

    const loadOlderEl = document.getElementById('chatLoadOlder');
    if (loadOlderEl) loadOlderEl.style.display = _chatHasMore ? 'flex' : 'none';
}

async function loadOlderMessages() {
    if (_chatLoadingOlder || !_chatHasMore) return;
    _chatLoadingOlder = true;

    const container = document.getElementById('chatMessages');
    const scrollHeightBefore = container.scrollHeight;

    const data = await api(`/api/chat/history?limit=${_chatPageSize}&offset=${_chatOffset}`);
    _chatLoadingOlder = false;
    if (!data || !data.messages || data.messages.length === 0) return;

    Object.assign(chatExpenseMap, data.expenses || {});
    _chatHasMore = data.hasMore || false;
    _chatOffset += data.messages.length;

    // Prepend messages (they arrive in chronological order)
    const firstBubble = container.firstElementChild;
    for (let i = data.messages.length - 1; i >= 0; i--) {
        const msg = data.messages[i];
        const bubble = buildChatBubble(msg.role === 'USER' ? 'user' : 'bot', msg.text, msg.linkedExpenseIds, msg.createdAt);
        container.insertBefore(bubble, firstBubble);
    }

    // Restore scroll position so it doesn't jump to top
    container.scrollTop = container.scrollHeight - scrollHeightBefore;

    const loadOlderEl = document.getElementById('chatLoadOlder');
    if (loadOlderEl) loadOlderEl.style.display = _chatHasMore ? 'flex' : 'none';
}

function formatChatTime(isoString) {
    if (!isoString) return '';
    const d = new Date(isoString);
    const now = new Date();
    const isToday = d.toDateString() === now.toDateString();
    const yesterday = new Date(now); yesterday.setDate(now.getDate() - 1);
    const isYesterday = d.toDateString() === yesterday.toDateString();

    const time = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    if (isToday) return time;
    if (isYesterday) return `Yesterday ${time}`;
    return `${d.toLocaleDateString([], { month: 'short', day: 'numeric' })} ${time}`;
}

function buildChatBubble(role, text, linkedExpenseIds, timestamp) {
    const bubble = document.createElement('div');
    bubble.className = `chat-bubble ${role}`;

    let html = text.replace(/\n/g, '<br>');
    const timeStr = formatChatTime(timestamp);
    bubble.innerHTML = `<p>${html}</p>${timeStr ? `<span class="chat-timestamp">${timeStr}</span>` : ''}`;

    if (role === 'bot' && linkedExpenseIds && linkedExpenseIds.length > 0) {
        const cardsHtml = linkedExpenseIds.map(id => {
            const exp = chatExpenseMap[id];
            if (!exp) return '';
            return `<a href="#/expenses/${exp.urlId}" class="chat-expense-card" onclick="hideMobileUI()">
                <div class="chat-card-icon"><i class="fa-solid fa-${categoryIcon(exp.category)}"></i></div>
                <div class="chat-card-info">
                    <div class="chat-card-cat">${exp.category || 'Expense'}</div>
                    <div class="chat-card-note">${exp.notes || ''}</div>
                </div>
                <div class="chat-card-amount">${exp.amount != null ? Number(exp.amount).toFixed(2) : '-'} ${exp.currency || ''}</div>
            </a>`;
        }).join('');
        bubble.innerHTML += cardsHtml;
    }
    return bubble;
}

function appendChatBubble(role, text, linkedExpenseIds, timestamp) {
    const container = document.getElementById('chatMessages');
    if (!container) return;
    const bubble = buildChatBubble(role, text, linkedExpenseIds, timestamp);
    container.appendChild(bubble);
    container.scrollTop = container.scrollHeight;
}

function chatKeydown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendChat();
    }
}

async function sendChat() {
    const input = document.getElementById('chatInput');
    const message = input.value.trim();
    if (!message) return;
    input.value = '';

    appendChatBubble('user', message, [], new Date().toISOString());

    const container = document.getElementById('chatMessages');
    const typing = document.createElement('div');
    typing.className = 'chat-bubble bot chat-typing';
    typing.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Thinking...';
    container.appendChild(typing);
    container.scrollTop = container.scrollHeight;

    const result = await api('/api/chat', { method: 'POST', body: { message } });
    typing.remove();

    if (result && result.message) {
        if (result.expenseCards) {
            for (const card of result.expenseCards) {
                chatExpenseMap[card.id] = card;
            }
        }
        const ts = result.message.createdAt || new Date().toISOString();
        appendChatBubble('bot', result.message.text, result.message.linkedExpenseIds, ts);
        _chatOffset += 2; // user + bot messages added
    } else {
        appendChatBubble('bot', 'Sorry, something went wrong. Please try again.', [], new Date().toISOString());
        _chatOffset += 1;
    }
}

// --- Desktop chat toggle ---
let desktopChatOpen = false;

function toggleDesktopChat() {
    const chatPanel = document.getElementById('desktopChatPanel');
    if (!chatPanel) return;
    desktopChatOpen = !desktopChatOpen;
    chatPanel.style.display = desktopChatOpen ? 'flex' : 'none';
    if (desktopChatOpen && !chatPanel.dataset.initialized) {
        renderChatPanel(document.getElementById('desktopChatContent'));
        chatPanel.dataset.initialized = 'true';
    }
}


