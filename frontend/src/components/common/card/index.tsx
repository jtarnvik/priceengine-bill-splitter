import { ReactNode } from 'react';

type Props = {
  children: ReactNode
}

export function Card({ children }: Props) {
  return (
    <div className="block max-w px-4 py-1 bg-[#F1F2F3] border border-gray-200 rounded-lg shadow-sm">
      <div className="text-gray-800">
        {children}
      </div>
    </div>
  );
}
