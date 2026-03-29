'use client';

import React, { useEffect, useState, useRef, useCallback } from 'react';
import { apiClient } from '@gateway/shared-ui/lib/api-client';

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8082';

interface AiModel {
  id: string;
  name: string;
  provider: string;
  maxTokens?: number;
}

interface AiProvider {
  id: string;
  name: string;
  models?: string[];
}

interface ChatMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
}

interface TokenUsage {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
}

interface ChatResponse {
  choices: { message: { role: string; content: string } }[];
  usage?: TokenUsage;
}

interface TokenBudget {
  used: number;
  limit: number;
  remaining: number;
  resetAt?: string;
}

export default function AiPlaygroundPage() {
  const [models, setModels] = useState<AiModel[]>([]);
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Chat state
  const [selectedModel, setSelectedModel] = useState('');
  const [selectedProvider, setSelectedProvider] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputMessage, setInputMessage] = useState('');
  const [sending, setSending] = useState(false);
  const [lastUsage, setLastUsage] = useState<TokenUsage | null>(null);

  // Settings
  const [temperature, setTemperature] = useState(0.7);
  const [maxTokens, setMaxTokens] = useState(1024);
  const [streamEnabled, setStreamEnabled] = useState(false);

  // Token budget
  const [tokenBudget, setTokenBudget] = useState<TokenBudget | null>(null);

  // Streaming
  const [streamingContent, setStreamingContent] = useState('');
  const abortRef = useRef<AbortController | null>(null);
  const chatEndRef = useRef<HTMLDivElement>(null);

  const getToken = (): string | null => {
    return typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  };

  const fetchTokenBudget = useCallback(async () => {
    try {
      const data = await apiClient<TokenBudget>('/v1/ai/token-budget');
      setTokenBudget(data);
    } catch {
      // Token budget may not be available
    }
  }, []);

  useEffect(() => {
    async function init() {
      try {
        const token = getToken();
        const headers: Record<string, string> = { 'Content-Type': 'application/json' };
        if (token) headers['Authorization'] = `Bearer ${token}`;

        const [modelsRes, providersRes] = await Promise.all([
          fetch(`${GATEWAY_URL}/v1/ai/models`, { headers }),
          fetch(`${GATEWAY_URL}/v1/ai/providers`, { headers }),
        ]);

        if (modelsRes.ok) {
          const data = await modelsRes.json();
          setModels(Array.isArray(data) ? data : data.content ?? data.models ?? []);
        }
        if (providersRes.ok) {
          const data = await providersRes.json();
          setProviders(Array.isArray(data) ? data : data.content ?? data.providers ?? []);
        }
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Failed to load AI configuration');
      } finally {
        setLoading(false);
      }
    }
    init();
    fetchTokenBudget();
  }, [fetchTokenBudget]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streamingContent]);

  const estimateCost = (): string => {
    const inputTokens = inputMessage.split(/\s+/).length * 1.3;
    const totalEstimate = inputTokens + maxTokens;
    const costPer1k = 0.002;
    const estimated = (totalEstimate / 1000) * costPer1k;
    return `~$${estimated.toFixed(4)}`;
  };

  const handleSend = async () => {
    if (!inputMessage.trim() || !selectedModel || sending) return;

    const userMessage: ChatMessage = { role: 'user', content: inputMessage.trim() };
    const updatedMessages = [...messages, userMessage];
    setMessages(updatedMessages);
    setInputMessage('');
    setSending(true);
    setError('');
    setLastUsage(null);

    const token = getToken();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const body = {
      model: selectedModel,
      messages: updatedMessages.map((m) => ({ role: m.role, content: m.content })),
      temperature,
      maxTokens,
      stream: streamEnabled,
    };

    try {
      if (streamEnabled) {
        // SSE streaming
        setStreamingContent('');
        const controller = new AbortController();
        abortRef.current = controller;

        const res = await fetch(`${GATEWAY_URL}/v1/ai/chat/completions`, {
          method: 'POST',
          headers,
          body: JSON.stringify(body),
          signal: controller.signal,
        });

        if (!res.ok) {
          const errBody = await res.text();
          throw new Error(errBody || `Request failed (${res.status})`);
        }

        const reader = res.body?.getReader();
        const decoder = new TextDecoder();
        let accumulated = '';

        if (reader) {
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            const chunk = decoder.decode(value, { stream: true });
            const lines = chunk.split('\n');
            for (const line of lines) {
              if (line.startsWith('data: ')) {
                const data = line.slice(6);
                if (data === '[DONE]') break;
                try {
                  const parsed = JSON.parse(data);
                  const delta = parsed.choices?.[0]?.delta?.content || '';
                  accumulated += delta;
                  setStreamingContent(accumulated);
                } catch {
                  // Non-JSON data line, append raw
                  accumulated += data;
                  setStreamingContent(accumulated);
                }
              }
            }
          }
        }

        const assistantMessage: ChatMessage = { role: 'assistant', content: accumulated };
        setMessages((prev) => [...prev, assistantMessage]);
        setStreamingContent('');
        abortRef.current = null;
      } else {
        // Non-streaming
        const res = await fetch(`${GATEWAY_URL}/v1/ai/chat/completions`, {
          method: 'POST',
          headers,
          body: JSON.stringify(body),
        });

        if (!res.ok) {
          const errBody = await res.text();
          throw new Error(errBody || `Request failed (${res.status})`);
        }

        const data: ChatResponse = await res.json();
        const assistantContent = data.choices?.[0]?.message?.content || 'No response received.';
        const assistantMessage: ChatMessage = { role: 'assistant', content: assistantContent };
        setMessages((prev) => [...prev, assistantMessage]);

        if (data.usage) {
          setLastUsage(data.usage);
        }
      }

      fetchTokenBudget();
    } catch (err: unknown) {
      if (err instanceof Error && err.name === 'AbortError') {
        // User cancelled
      } else {
        setError(err instanceof Error ? err.message : 'Failed to send message');
      }
    } finally {
      setSending(false);
    }
  };

  const handleClearChat = () => {
    setMessages([]);
    setLastUsage(null);
    setStreamingContent('');
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
  };

  if (loading) return <p>Loading...</p>;

  const sectionStyle: React.CSSProperties = {
    backgroundColor: '#fff',
    borderRadius: 8,
    border: '1px solid #e2e8f0',
    padding: 20,
  };

  const labelStyle: React.CSSProperties = {
    display: 'block',
    fontSize: 13,
    color: '#64748b',
    marginBottom: 4,
  };

  const selectStyle: React.CSSProperties = {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #cbd5e1',
    borderRadius: 6,
    fontSize: 14,
    boxSizing: 'border-box',
  };

  const budgetPercentage = tokenBudget ? Math.min((tokenBudget.used / tokenBudget.limit) * 100, 100) : 0;

  return (
    <div>
      <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', marginBottom: 20 }}>AI Playground</h1>

      {error && <p style={{ color: '#dc2626', marginBottom: 12 }}>{error}</p>}

      <div style={{ display: 'grid', gridTemplateColumns: '300px 1fr', gap: 24 }}>
        {/* Left: Configuration */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Provider Selection */}
          <div style={sectionStyle}>
            <h3 style={{ fontSize: 14, fontWeight: 600, color: '#334155', marginBottom: 12, marginTop: 0 }}>
              Provider
            </h3>
            <select
              value={selectedProvider}
              onChange={(e) => setSelectedProvider(e.target.value)}
              style={selectStyle}
            >
              <option value="">-- Select Provider --</option>
              {providers.map((p) => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          </div>

          {/* Model Selection */}
          <div style={sectionStyle}>
            <h3 style={{ fontSize: 14, fontWeight: 600, color: '#334155', marginBottom: 12, marginTop: 0 }}>
              Model
            </h3>
            <select
              value={selectedModel}
              onChange={(e) => setSelectedModel(e.target.value)}
              style={selectStyle}
            >
              <option value="">-- Select Model --</option>
              {models
                .filter((m) => !selectedProvider || m.provider === selectedProvider)
                .map((m) => (
                  <option key={m.id} value={m.id}>{m.name}</option>
                ))}
            </select>
          </div>

          {/* Parameters */}
          <div style={sectionStyle}>
            <h3 style={{ fontSize: 14, fontWeight: 600, color: '#334155', marginBottom: 12, marginTop: 0 }}>
              Parameters
            </h3>

            <div style={{ marginBottom: 14 }}>
              <label style={labelStyle}>Temperature: {temperature.toFixed(1)}</label>
              <input
                type="range"
                min="0"
                max="2"
                step="0.1"
                value={temperature}
                onChange={(e) => setTemperature(parseFloat(e.target.value))}
                style={{ width: '100%' }}
              />
            </div>

            <div style={{ marginBottom: 14 }}>
              <label style={labelStyle}>Max Tokens</label>
              <input
                type="number"
                value={maxTokens}
                onChange={(e) => setMaxTokens(parseInt(e.target.value, 10) || 256)}
                min={1}
                max={128000}
                style={{
                  width: '100%',
                  padding: '8px 12px',
                  border: '1px solid #cbd5e1',
                  borderRadius: 6,
                  fontSize: 14,
                  boxSizing: 'border-box',
                }}
              />
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input
                type="checkbox"
                id="streamToggle"
                checked={streamEnabled}
                onChange={(e) => setStreamEnabled(e.target.checked)}
              />
              <label htmlFor="streamToggle" style={{ fontSize: 13, color: '#334155', cursor: 'pointer' }}>
                Enable Streaming
              </label>
            </div>
          </div>

          {/* Token Budget */}
          {tokenBudget && (
            <div style={sectionStyle}>
              <h3 style={{ fontSize: 14, fontWeight: 600, color: '#334155', marginBottom: 12, marginTop: 0 }}>
                Token Budget
              </h3>
              <div style={{ marginBottom: 8 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: '#64748b', marginBottom: 4 }}>
                  <span>{tokenBudget.used.toLocaleString()} used</span>
                  <span>{tokenBudget.limit.toLocaleString()} limit</span>
                </div>
                <div style={{ height: 8, backgroundColor: '#e2e8f0', borderRadius: 4, overflow: 'hidden' }}>
                  <div
                    style={{
                      height: '100%',
                      width: `${budgetPercentage}%`,
                      backgroundColor: budgetPercentage > 90 ? '#ef4444' : budgetPercentage > 70 ? '#f59e0b' : '#10b981',
                      borderRadius: 4,
                      transition: 'width 0.3s',
                    }}
                  />
                </div>
              </div>
              <div style={{ fontSize: 12, color: '#64748b' }}>
                {tokenBudget.remaining.toLocaleString()} tokens remaining
                {tokenBudget.resetAt && (
                  <span> &middot; Resets {new Date(tokenBudget.resetAt).toLocaleDateString()}</span>
                )}
              </div>
            </div>
          )}

          {/* Cost Estimate */}
          <div style={sectionStyle}>
            <h3 style={{ fontSize: 14, fontWeight: 600, color: '#334155', marginBottom: 8, marginTop: 0 }}>
              Cost Estimate
            </h3>
            <span style={{ fontSize: 20, fontWeight: 700, color: '#0f172a' }}>{estimateCost()}</span>
            <span style={{ fontSize: 12, color: '#64748b', marginLeft: 8 }}>per request</span>
          </div>
        </div>

        {/* Right: Chat */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Conversation History */}
          <div
            style={{
              ...sectionStyle,
              flex: 1,
              minHeight: 400,
              maxHeight: 600,
              overflow: 'auto',
              display: 'flex',
              flexDirection: 'column',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <h3 style={{ fontSize: 14, fontWeight: 600, color: '#334155', margin: 0 }}>
                Conversation
              </h3>
              <button
                onClick={handleClearChat}
                style={{
                  padding: '4px 10px',
                  fontSize: 12,
                  backgroundColor: '#f1f5f9',
                  border: '1px solid #cbd5e1',
                  borderRadius: 4,
                  cursor: 'pointer',
                  color: '#475569',
                }}
              >
                Clear
              </button>
            </div>

            <div style={{ flex: 1, overflow: 'auto' }}>
              {messages.length === 0 && !streamingContent && (
                <p style={{ color: '#94a3b8', fontSize: 14, textAlign: 'center', marginTop: 60 }}>
                  Select a model and start chatting.
                </p>
              )}

              {messages.map((msg, i) => (
                <div
                  key={i}
                  style={{
                    marginBottom: 12,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: msg.role === 'user' ? 'flex-end' : 'flex-start',
                  }}
                >
                  <span style={{ fontSize: 11, color: '#94a3b8', marginBottom: 2, textTransform: 'uppercase' }}>
                    {msg.role}
                  </span>
                  <div
                    style={{
                      padding: '10px 14px',
                      borderRadius: 8,
                      maxWidth: '80%',
                      fontSize: 14,
                      lineHeight: 1.6,
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                      backgroundColor: msg.role === 'user' ? '#3b82f6' : '#f1f5f9',
                      color: msg.role === 'user' ? '#fff' : '#0f172a',
                    }}
                  >
                    {msg.content}
                  </div>
                </div>
              ))}

              {/* Streaming indicator */}
              {streamingContent && (
                <div style={{ marginBottom: 12, display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
                  <span style={{ fontSize: 11, color: '#94a3b8', marginBottom: 2, textTransform: 'uppercase' }}>
                    assistant
                  </span>
                  <div
                    style={{
                      padding: '10px 14px',
                      borderRadius: 8,
                      maxWidth: '80%',
                      fontSize: 14,
                      lineHeight: 1.6,
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                      backgroundColor: '#f1f5f9',
                      color: '#0f172a',
                    }}
                  >
                    {streamingContent}
                    <span style={{ animation: 'blink 1s infinite', color: '#3b82f6' }}>|</span>
                  </div>
                </div>
              )}

              {sending && !streamEnabled && (
                <div style={{ marginBottom: 12, display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
                  <span style={{ fontSize: 11, color: '#94a3b8', marginBottom: 2, textTransform: 'uppercase' }}>
                    assistant
                  </span>
                  <div
                    style={{
                      padding: '10px 14px',
                      borderRadius: 8,
                      fontSize: 14,
                      backgroundColor: '#f1f5f9',
                      color: '#94a3b8',
                    }}
                  >
                    Thinking...
                  </div>
                </div>
              )}

              <div ref={chatEndRef} />
            </div>
          </div>

          {/* Token Usage */}
          {lastUsage && (
            <div
              style={{
                display: 'flex',
                gap: 16,
                padding: '10px 16px',
                backgroundColor: '#f8fafc',
                border: '1px solid #e2e8f0',
                borderRadius: 6,
                fontSize: 13,
              }}
            >
              <span style={{ color: '#64748b' }}>
                Prompt: <strong style={{ color: '#0f172a' }}>{lastUsage.promptTokens}</strong>
              </span>
              <span style={{ color: '#64748b' }}>
                Completion: <strong style={{ color: '#0f172a' }}>{lastUsage.completionTokens}</strong>
              </span>
              <span style={{ color: '#64748b' }}>
                Total: <strong style={{ color: '#0f172a' }}>{lastUsage.totalTokens}</strong>
              </span>
            </div>
          )}

          {/* Input */}
          <div style={{ display: 'flex', gap: 8 }}>
            <input
              type="text"
              value={inputMessage}
              onChange={(e) => setInputMessage(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); } }}
              placeholder="Type your message..."
              disabled={sending}
              style={{
                flex: 1,
                padding: '10px 14px',
                border: '1px solid #cbd5e1',
                borderRadius: 6,
                fontSize: 14,
                boxSizing: 'border-box',
              }}
            />
            <button
              onClick={handleSend}
              disabled={!inputMessage.trim() || !selectedModel || sending}
              style={{
                padding: '10px 24px',
                backgroundColor: !inputMessage.trim() || !selectedModel || sending ? '#94a3b8' : '#3b82f6',
                color: '#fff',
                border: 'none',
                borderRadius: 6,
                fontSize: 14,
                fontWeight: 600,
                cursor: !inputMessage.trim() || !selectedModel || sending ? 'not-allowed' : 'pointer',
              }}
            >
              {sending ? 'Sending...' : 'Send'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
