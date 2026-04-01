/* ============================================
   Expense Tracker - Auth (Login/Register)
   ============================================ */

function renderLogin(app) {
    document.getElementById('navbar').style.display = 'none';
    app.innerHTML = `
    <div class="auth-container">
        <div class="card">
            <h2 class="card-title"><i class="fa-solid fa-wallet"></i> Expense Tracker</h2>
            <form id="loginForm">
                <div class="form-group">
                    <label><i class="fa-solid fa-user"></i> Username</label>
                    <input type="text" class="form-control" id="loginUsername" required>
                </div>
                <div class="form-group">
                    <label><i class="fa-solid fa-lock"></i> Password</label>
                    <input type="password" class="form-control" id="loginPassword" required>
                </div>
                <button type="submit" class="btn btn-primary" style="width:100%">
                    <i class="fa-solid fa-right-to-bracket"></i> Login
                </button>
            </form>
            <p class="auth-switch">Don't have an account? <a href="#/register">Register</a></p>
        </div>
    </div>`;
    document.getElementById('loginForm').onsubmit = async (e) => {
        e.preventDefault();
        const data = await api('/api/auth/login', { method: 'POST', body: {
            username: document.getElementById('loginUsername').value,
            password: document.getElementById('loginPassword').value
        }});
        if (data && data.userId) { toast('Welcome back!', 'success'); navigate('#/dashboard'); }
        else toast(data?.error || 'Login failed', 'error');
    };
}

function renderRegister(app) {
    document.getElementById('navbar').style.display = 'none';
    app.innerHTML = `
    <div class="auth-container">
        <div class="card">
            <h2 class="card-title"><i class="fa-solid fa-user-plus"></i> Register</h2>
            <form id="registerForm">
                <div class="form-group">
                    <label>Username</label>
                    <input type="text" class="form-control" id="regUsername" required>
                </div>
                <div class="form-group">
                    <label>Password</label>
                    <input type="password" class="form-control" id="regPassword" required>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Email</label>
                        <input type="email" class="form-control" id="regEmail">
                    </div>
                    <div class="form-group">
                        <label>Phone</label>
                        <input type="text" class="form-control" id="regPhone" placeholder="+65...">
                    </div>
                </div>
                <div class="form-group">
                    <label>Base Currency</label>
                    <select class="form-control" id="regCurrency">
                        <option value="USD">USD</option><option value="EUR">EUR</option>
                        <option value="GBP">GBP</option><option value="SGD">SGD</option>
                        <option value="JPY">JPY</option><option value="AUD">AUD</option>
                        <option value="CAD">CAD</option><option value="CHF">CHF</option>
                    </select>
                </div>
                <button type="submit" class="btn btn-primary" style="width:100%">
                    <i class="fa-solid fa-user-plus"></i> Register
                </button>
            </form>
            <p class="auth-switch">Already have an account? <a href="#/login">Login</a></p>
        </div>
    </div>`;
    document.getElementById('registerForm').onsubmit = async (e) => {
        e.preventDefault();
        const data = await api('/api/auth/register', { method: 'POST', body: {
            username: document.getElementById('regUsername').value,
            password: document.getElementById('regPassword').value,
            email: document.getElementById('regEmail').value,
            phoneNumber: document.getElementById('regPhone').value,
            baseCurrency: document.getElementById('regCurrency').value
        }});
        if (data && data.userId) { toast('Registration successful! Please login.', 'success'); navigate('#/login'); }
        else toast(data?.error || 'Registration failed', 'error');
    };
}

async function renderProfile(app) {
    const user = await api('/api/auth/me');
    if (!user) return;
    const currentTheme = getTheme();
    app.innerHTML = `
    <div class="container">
        <div class="auth-container" style="margin-top:1rem">
            <div class="card">
                <h2 class="card-title"><i class="fa-solid fa-user-gear"></i> Profile</h2>
                <form id="profileForm">
                    <div class="form-group">
                        <label>Username</label>
                        <input type="text" class="form-control" value="${esc(user.username)}" disabled>
                    </div>
                    <div class="form-group">
                        <label>Email</label>
                        <input type="email" class="form-control" id="pEmail" value="${esc(user.email)}">
                    </div>
                    <div class="form-group">
                        <label>Phone Number</label>
                        <input type="text" class="form-control" id="pPhone" value="${esc(user.phoneNumber)}">
                    </div>
                    <div class="form-row-3">
                        <div class="form-group">
                            <label>Base Currency</label>
                            <select class="form-control" id="pCurrency">
                                ${['USD','EUR','GBP','SGD','JPY','AUD','CAD','CHF'].map(c => `<option value="${c}" ${c===user.baseCurrency?'selected':''}>${c}</option>`).join('')}
                            </select>
                        </div>
                        <div class="form-group">
                            <label>Base City</label>
                            <input type="text" class="form-control" id="pBaseCity" value="${esc(user.baseCity)}" placeholder="e.g. Singapore">
                        </div>
                        <div class="form-group">
                            <label>Base Country</label>
                            <input type="text" class="form-control" id="pBaseCountry" value="${esc(user.baseCountry)}" placeholder="e.g. SG">
                        </div>
                    </div>
                    <div class="form-group">
                        <label>New Password (leave blank to keep)</label>
                        <input type="password" class="form-control" id="pPassword">
                    </div>
                    <button type="submit" class="btn btn-primary" style="width:100%">
                        <i class="fa-solid fa-save"></i> Update Profile
                    </button>
                </form>
            </div>
            <div class="card">
                <h2 class="card-title"><i class="fa-solid fa-palette"></i> Appearance</h2>
                <div class="form-group">
                    <label>Theme</label>
                    <div class="theme-selector">
                        <button class="theme-btn ${currentTheme === 'light' ? 'active' : ''}" onclick="setTheme('light'); updateThemeBtns()">
                            <i class="fa-solid fa-sun"></i> Light
                        </button>
                        <button class="theme-btn ${currentTheme === 'system' ? 'active' : ''}" onclick="setTheme('system'); updateThemeBtns()">
                            <i class="fa-solid fa-circle-half-stroke"></i> System
                        </button>
                        <button class="theme-btn ${currentTheme === 'dark' ? 'active' : ''}" onclick="setTheme('dark'); updateThemeBtns()">
                            <i class="fa-solid fa-moon"></i> Dark
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </div>`;
    document.getElementById('profileForm').onsubmit = async (ev) => {
        ev.preventDefault();
        const body = {
            email: document.getElementById('pEmail').value,
            phoneNumber: document.getElementById('pPhone').value,
            baseCurrency: document.getElementById('pCurrency').value,
            baseCity: document.getElementById('pBaseCity').value,
            baseCountry: document.getElementById('pBaseCountry').value,
            password: document.getElementById('pPassword').value
        };
        await api('/api/user/profile', { method: 'PUT', body });
        toast('Profile updated!', 'success');
        await checkAuth();
    };
}

function updateThemeBtns() {
    const current = getTheme();
    document.querySelectorAll('.theme-btn').forEach(btn => {
        const icon = btn.querySelector('i');
        const isLight = icon.classList.contains('fa-sun');
        const isSystem = icon.classList.contains('fa-circle-half-stroke');
        const isDark = icon.classList.contains('fa-moon');
        btn.classList.toggle('active',
            (current === 'light' && isLight) ||
            (current === 'system' && isSystem) ||
            (current === 'dark' && isDark)
        );
    });
}

