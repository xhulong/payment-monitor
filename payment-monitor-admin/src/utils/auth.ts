const TokenKey = 'Admin-Token';

let memoryToken: string | null = null;

export const getToken = () => memoryToken;

export const setToken = (access_token: string) => {
  memoryToken = access_token;
};

export const removeToken = () => {
  memoryToken = null;
  localStorage.removeItem(TokenKey);
  sessionStorage.removeItem(TokenKey);
};
