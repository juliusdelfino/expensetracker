package com.delfino.expensetracker.exception;

public class AiModelNotAllowedException extends IllegalArgumentException {

    public AiModelNotAllowedException(String modelId) {
        super("Unsupported AI model: " + modelId);
    }
}

