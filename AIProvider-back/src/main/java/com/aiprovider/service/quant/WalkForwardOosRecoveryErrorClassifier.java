package com.aiprovider.service.quant;

import java.sql.SQLTransientException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.QueryTimeoutException;

final class WalkForwardOosRecoveryErrorClassifier {
  private WalkForwardOosRecoveryErrorClassifier() {}

  static Kind classify(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof CannotAcquireLockException || current instanceof DeadlockLoserDataAccessException
          || current instanceof DataAccessResourceFailureException || current instanceof QueryTimeoutException
          || current instanceof SQLTransientException) return Kind.RETRYABLE_BATCH_ERROR;
      current = current.getCause();
    }
    if (failure instanceof DataAccessException) return Kind.RETRYABLE_BATCH_ERROR;
    return Kind.PERMANENT_STUDY_ERROR;
  }

  enum Kind { RETRYABLE_BATCH_ERROR, PERMANENT_STUDY_ERROR }
}
