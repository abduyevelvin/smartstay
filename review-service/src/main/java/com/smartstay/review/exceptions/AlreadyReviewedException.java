package com.smartstay.review.exceptions;

public class AlreadyReviewedException extends RuntimeException {

    public AlreadyReviewedException(String message) {
        super(message);
    }
}
