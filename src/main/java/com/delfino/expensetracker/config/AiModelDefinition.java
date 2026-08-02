package com.delfino.expensetracker.config;

public class AiModelDefinition {

    private String id;
    private String label;
    private AiProviderType provider;
    private boolean supportsChat;
    private boolean supportsOcr;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public AiProviderType getProvider() {
        return provider;
    }

    public void setProvider(AiProviderType provider) {
        this.provider = provider;
    }

    public boolean isSupportsChat() {
        return supportsChat;
    }

    public void setSupportsChat(boolean supportsChat) {
        this.supportsChat = supportsChat;
    }

    public boolean isSupportsOcr() {
        return supportsOcr;
    }

    public void setSupportsOcr(boolean supportsOcr) {
        this.supportsOcr = supportsOcr;
    }
}

