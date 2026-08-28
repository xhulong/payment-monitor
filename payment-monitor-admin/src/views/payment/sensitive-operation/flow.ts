export type RequestStepUp = (
  operation: string,
  title?: string
) => Promise<string | undefined>;

export const runMfaSensitiveOperation = async <T>(
  operation: string,
  title: string,
  requestStepUp: RequestStepUp,
  submit: (stepUpToken?: string) => Promise<T>
): Promise<T> => {
  const token = await requestStepUp(operation, title);
  return submit(token);
};

export const runOptionalMfaOperation = async <T>(
  requiresMfa: boolean,
  operation: string,
  title: string,
  requestStepUp: RequestStepUp,
  submit: (stepUpToken?: string) => Promise<T>
): Promise<T> => {
  if (!requiresMfa) {
    return submit();
  }
  return runMfaSensitiveOperation(
    operation,
    title,
    requestStepUp,
    submit
  );
};
