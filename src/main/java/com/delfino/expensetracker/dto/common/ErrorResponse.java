package com.delfino.expensetracker.dto.common;

public record ErrorResponse(String error, String code) {

	public ErrorResponse(String error) {
		this(error, null);
	}
}
