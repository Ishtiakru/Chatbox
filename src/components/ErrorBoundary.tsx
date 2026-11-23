import React from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface ErrorBoundaryState {
  hasError: boolean;
  message: string;
}

export class ErrorBoundary extends React.Component<
  { children: React.ReactNode },
  ErrorBoundaryState
> {
  constructor(props: { children: React.ReactNode }) {
    super(props);
    this.state = { hasError: false, message: '' };
  }

  static getDerivedStateFromError(error: unknown): ErrorBoundaryState {
    const raw = error instanceof Error ? error.message : String(error);
    let friendly = raw;
    try {
      const parsed = JSON.parse(raw) as { error?: string; operationType?: string; path?: string };
      if (parsed.error) {
        friendly = `${parsed.error}${parsed.path ? ` (${parsed.operationType ?? 'op'} on ${parsed.path})` : ''}`;
      }
    } catch {
      // Not JSON
    }
    return { hasError: true, message: friendly };
  }

  override render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-[#f6f4fa] p-6">
          <div className="max-w-md w-full rounded-2xl bg-white border border-[#ece9f3] p-6 shadow-sm text-center space-y-4">
            <div className="w-12 h-12 rounded-2xl bg-[#fff1ed] text-[#9b5145] mx-auto flex items-center justify-center">
              <AlertTriangle className="w-6 h-6" />
            </div>
            <h1 className="font-display text-lg font-bold text-[#2a2340]">
              Something went wrong in Chatbox
            </h1>
            <p className="text-sm text-[#5a5468] break-words">
              {this.state.message || 'An unexpected error occurred.'}
            </p>
            <button
              type="button"
              onClick={() => window.location.reload()}
              className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-[#6250a0] hover:bg-[#463a76] text-white text-sm font-medium transition-colors cursor-pointer"
            >
              <RefreshCw className="w-4 h-4" />
              Reload Chatbox
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
