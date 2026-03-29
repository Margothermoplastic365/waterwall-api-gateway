import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Waterwall API Gateway - Developer Portal',
  description: 'Discover, subscribe to, and manage APIs through the Waterwall API Gateway Developer Portal',
  keywords: ['API', 'gateway', 'developer portal', 'API management', 'API catalog'],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
