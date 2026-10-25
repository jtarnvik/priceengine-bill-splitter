import { ReactNode } from 'react';

type ViewProps = {
  children: ReactNode;
  className?: string;
};

export function View({ children, className }: ViewProps) {
  return (
    <main className={`flex flex-col gap-2 px-2 pt-1${className ? ` ${className}` : ''}`}>
      {children}
    </main>
  );
}
