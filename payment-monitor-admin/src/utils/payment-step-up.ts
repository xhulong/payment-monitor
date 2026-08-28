import { ElMessageBox } from 'element-plus';
import { createStepUpToken, getTotpStatus } from '@/api/payment';

export const requestPaymentStepUp = async (
  operation: string,
  title = 'MFA 二次验证'
): Promise<string | undefined> => {
  const enabled = Boolean((await getTotpStatus()).data);
  if (!enabled) {
    return undefined;
  }
  const result = await ElMessageBox.prompt(
    '请输入身份验证器中当前的 6 位验证码。验证码验证成功后，本次敏感操作授权 5 分钟。',
    title,
    {
      confirmButtonText: '验证并继续',
      cancelButtonText: '取消',
      inputPlaceholder: '000000',
      inputPattern: /^\d{6}$/,
      inputErrorMessage: '请输入 6 位数字验证码',
      closeOnClickModal: false
    }
  );
  const response = await createStepUpToken(operation, result.value);
  const token = response.data?.token;
  if (!token) {
    throw new Error('未取得二次验证令牌');
  }
  return token;
};
