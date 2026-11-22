// Prevent an operation that has not started yet from crossing an auth change.
// It cannot cancel a request already sent; Firebase remains the authority.
export function createAccountOperationGuard(isCurrent) {
  if (typeof isCurrent !== 'function') throw new Error('An account check is required.');
  return {
    run(operation) {
      if (!isCurrent()) throw new Error('The signed-in account changed.');
      return operation();
    },
  };
}
