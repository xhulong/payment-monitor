export type RequestMfaStepUp = (
  operation: string,
  title?: string
) => Promise<string | undefined>;

export const startMfaSetupFlow = async <T>(
  mfaEnabled: boolean,
  requestStepUp: RequestMfaStepUp,
  setup: (stepUpToken?: string) => Promise<T>
): Promise<T> => {
  const stepUpToken = mfaEnabled
    ? await requestStepUp('MFA_REPLACE', '重新配置 MFA')
    : undefined;
  return setup(stepUpToken);
};
