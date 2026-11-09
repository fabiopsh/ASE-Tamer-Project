import { createContext, useContext } from 'react';

export type ToastTone = 'success' | 'error' | 'info' | 'warning';

export interface ToastInput {
  title: string;
  message?: string;
  tone?: ToastTone;
  /** Milliseconds before auto-dismiss (default 4500; 0 = sticky). */
  duration?: number;
}

export interface ToastApi {
  show: (toast: ToastInput) => void;
}

export const ToastContext = createContext<ToastApi | null>(null);

/** const toast = useToast(); toast.show({ title: 'Saved', tone: 'success' }) */
export function useToast(): ToastApi {
  const value = useContext(ToastContext);
  if (!value) throw new Error('useToast must be used inside <ToastProvider>.');
  return value;
}
