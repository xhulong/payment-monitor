export const maskRecoveryEmail = (email: string) => {
  const [local, domain] = email.split('@');
  if (!domain) return email;
  return `${local.slice(0, 1)}***@${domain}`;
};

export const passwordResetFormValid = (
  password: string,
  confirmation: string
) => password.length >= 12 && password.length <= 64 && password === confirmation;
