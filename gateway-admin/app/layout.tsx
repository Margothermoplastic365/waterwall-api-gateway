import type { Metadata } from 'next';
import './globals.css';
import { AppShell } from './components/AppShell';

export const metadata: Metadata = {
  title: 'Waterwall API Gateway - Admin Console',
  description: 'Platform administration console for Waterwall API Gateway',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-slate-50">
        <AppShell>{children}</AppShell>
      </body>
    </html>
  );
}
