'use client';

import React, { useEffect, useState, useRef, useCallback } from 'react';
import { apiClient } from '@gateway/shared-ui/lib/api-client';

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8082';

interface EventApi {
  id: string;
  name: string;
  description?: string;
  protocol: string;
  topics: string[];
  status?: string;
}

interface StreamEvent {
  timestamp: string;
  data: string;
  topic?: string;
}

export default function EventExplorerPage() {
  const [eventApis, setEventApis] = useState<EventApi[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Stream state
  const [selectedTopic, setSelectedTopic] = useState('');
  const [streamConnected, setStreamConnected] = useState(false);
  const [streamEvents, setStreamEvents] = useState<StreamEvent[]>([]);
  const sseRef = useRef<EventSource | null>(null);

  // Publish state
  const [publishTopic, setPublishTopic] = useState('');
  const [publishBody, setPublishBody] = useState('{\n  \n}');
  const [publishing, setPublishing] = useState(false);
  const [publishSuccess, setPublishSuccess] = useState('');
  const [publishError, setPublishError] = useState('');

  // Subscribe state
  const [subscribedTopics, setSubscribedTopics] = useState<Set<string>>(new Set());

  const streamEndRef = useRef<HTMLDivElement>(null);

  const getToken = (): string | null => {
    return typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  };

  useEffect(() => {
    async function fetchEventApis() {
      try {
        const data = await apiClient<{ content: EventApi[] }>('/v1/event-apis');
        const raw = Array.isArray(data) ? data : data.content ?? [];
        // Parse topics if they come as JSON strings from the backend
        const list = raw.map((api: EventApi) => ({
          ...api,
          topics: Array.isArray(api.topics) ? api.topics : typeof api.topics === 'string' ? (() => { try { const p = JSON.parse(api.topics as unknown as string); return Array.isArray(p) ? p : [api.topics]; } catch { return (api.topics as unknown as string).split(',').map(s => s.trim()).filter(Boolean); } })() : [],
        }));
        setEventApis(list);
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Failed to load event APIs');
      } finally {
        setLoading(false);
      }
    }
    fetchEventApis();
  }, []);

  useEffect(() => {
    streamEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [streamEvents]);

  // Cleanup SSE on unmount
  useEffect(() => {
    return () => {
      if (sseRef.current) {
        sseRef.current.close();
      }
    };
  }, []);

  const handleSubscribe = useCallback(async (topic: string) => {
    try {
      const token = getToken();
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (token) headers['Authorization'] = `Bearer ${token}`;

      await fetch(`${GATEWAY_URL}/v1/events/subscribe`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ topic }),
      });
      setSubscribedTopics((prev) => new Set(prev).add(topic));
    } catch {
      // Subscription may not require explicit call
      setSubscribedTopics((prev) => new Set(prev).add(topic));
    }
  }, []);

  const handleConnectStream = () => {
    if (!selectedTopic) return;
    if (sseRef.current) {
      sseRef.current.close();
    }

    const token = getToken();
    const url = `${GATEWAY_URL}/v1/events/stream/${encodeURIComponent(selectedTopic)}${token ? `?token=${encodeURIComponent(token)}` : ''}`;
    const es = new EventSource(url);

    es.onopen = () => {
      setStreamConnected(true);
      setStreamEvents((prev) => [...prev, {
        timestamp: new Date().toLocaleTimeString(),
        data: `[Connected to ${selectedTopic}]`,
      }]);
    };

    es.onmessage = (event) => {
      setStreamEvents((prev) => [...prev, {
        timestamp: new Date().toLocaleTimeString(),
        data: String(event.data),
        topic: selectedTopic,
      }]);
    };

    es.onerror = () => {
      setStreamConnected(false);
      setStreamEvents((prev) => [...prev, {
        timestamp: new Date().toLocaleTimeString(),
        data: '[Connection closed]',
      }]);
      es.close();
      sseRef.current = null;
    };

    sseRef.current = es;
  };

  const handleDisconnectStream = () => {
    if (sseRef.current) {
      sseRef.current.close();
      sseRef.current = null;
      setStreamConnected(false);
      setStreamEvents((prev) => [...prev, {
        timestamp: new Date().toLocaleTimeString(),
        data: '[Disconnected]',
      }]);
    }
  };

  const handlePublish = async () => {
    if (!publishTopic.trim()) return;
    setPublishing(true);
    setPublishError('');
    setPublishSuccess('');

    try {
      const token = getToken();
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (token) headers['Authorization'] = `Bearer ${token}`;

      const res = await fetch(`${GATEWAY_URL}/v1/events/publish`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ topic: publishTopic, data: publishBody }),
      });

      if (!res.ok) {
        const errBody = await res.text();
        throw new Error(errBody || `Publish failed (${res.status})`);
      }

      setPublishSuccess('Event published successfully');
      setTimeout(() => setPublishSuccess(''), 3000);
    } catch (err: unknown) {
      setPublishError(err instanceof Error ? err.message : 'Failed to publish event');
    } finally {
      setPublishing(false);
    }
  };

  const getProtocolBadge = (protocol: string): React.CSSProperties => {
    switch (protocol?.toUpperCase()) {
      case 'KAFKA':
        return { backgroundColor: '#0f172a', color: '#e2e8f0' };
      case 'MQTT':
        return { backgroundColor: '#dcfce7', color: '#166534' };
      case 'AMQP':
        return { backgroundColor: '#fef3c7', color: '#92400e' };
      case 'NATS':
        return { backgroundColor: '#dbeafe', color: '#1e40af' };
      case 'SSE':
        return { backgroundColor: '#cffafe', color: '#155e75' };
      case 'WEBSOCKET':
        return { backgroundColor: '#f3e8ff', color: '#6b21a8' };
      default:
        return { backgroundColor: '#f1f5f9', color: '#475569' };
    }
  };

  // Collect all topics from all event APIs
  const allTopics: string[] = [];
  eventApis.forEach((api) => {
    if (api.topics) {
      const topics = Array.isArray(api.topics) ? api.topics : typeof api.topics === 'string' ? (api.topics as string).split(',').map((s: string) => s.trim()).filter(Boolean) : [];
      topics.forEach((t: string) => {
        if (!allTopics.includes(t)) allTopics.push(t);
      });
    }
  });

  if (loading) return <p>Loading...</p>;

  const sectionStyle: React.CSSProperties = {
    backgroundColor: '#fff',
    borderRadius: 8,
    border: '1px solid #e2e8f0',
    padding: 20,
  };

  return (
    <div>
      <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', marginBottom: 20 }}>Event Explorer</h1>

      {error && <p style={{ color: '#dc2626', marginBottom: 12 }}>{error}</p>}

      {/* Event APIs List */}
      <div style={{ ...sectionStyle, marginBottom: 20 }}>
        <h2 style={{ fontSize: 16, fontWeight: 600, color: '#334155', marginTop: 0, marginBottom: 16 }}>
          Event APIs
        </h2>

        {eventApis.length === 0 ? (
          <p style={{ color: '#94a3b8', fontSize: 14 }}>No event APIs found.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {eventApis.map((api) => (
              <div
                key={api.id}
                style={{
                  padding: '14px 16px',
                  backgroundColor: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: 6,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <span style={{ fontSize: 15, fontWeight: 600, color: '#0f172a' }}>{api.name}</span>
                    <span
                      style={{
                        display: 'inline-block',
                        padding: '2px 10px',
                        borderRadius: 9999,
                        fontSize: 11,
                        fontWeight: 600,
                        ...getProtocolBadge(api.protocol),
                      }}
                    >
                      {api.protocol?.toUpperCase() || 'EVENT'}
                    </span>
                    {api.status && (
                      <span
                        style={{
                          display: 'inline-block',
                          padding: '2px 10px',
                          borderRadius: 9999,
                          fontSize: 11,
                          fontWeight: 600,
                          backgroundColor: api.status === 'PUBLISHED' ? '#dcfce7' : '#f1f5f9',
                          color: api.status === 'PUBLISHED' ? '#166534' : '#475569',
                        }}
                      >
                        {api.status}
                      </span>
                    )}
                  </div>
                </div>
                {api.description && (
                  <p style={{ fontSize: 13, color: '#64748b', margin: '0 0 8px 0' }}>{api.description}</p>
                )}
                {api.topics && Array.isArray(api.topics) && api.topics.length > 0 && (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                    {api.topics.map((topic: string) => (
                      <div key={topic} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        <code
                          style={{
                            fontSize: 12,
                            padding: '2px 8px',
                            backgroundColor: '#e2e8f0',
                            borderRadius: 4,
                            color: '#334155',
                          }}
                        >
                          {topic}
                        </code>
                        {!subscribedTopics.has(topic) ? (
                          <button
                            onClick={() => handleSubscribe(topic)}
                            style={{
                              padding: '2px 8px',
                              fontSize: 11,
                              backgroundColor: '#3b82f6',
                              color: '#fff',
                              border: 'none',
                              borderRadius: 4,
                              cursor: 'pointer',
                            }}
                          >
                            Subscribe
                          </button>
                        ) : (
                          <span
                            style={{
                              padding: '2px 8px',
                              fontSize: 11,
                              fontWeight: 600,
                              color: '#166534',
                            }}
                          >
                            Subscribed
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 24 }}>
        {/* Live Event Stream */}
        <div style={sectionStyle}>
          <h2 style={{ fontSize: 16, fontWeight: 600, color: '#334155', marginTop: 0, marginBottom: 16 }}>
            Live Event Stream
          </h2>

          <div style={{ marginBottom: 12 }}>
            <label style={{ display: 'block', fontSize: 13, color: '#64748b', marginBottom: 4 }}>Topic</label>
            <select
              value={selectedTopic}
              onChange={(e) => setSelectedTopic(e.target.value)}
              style={{
                width: '100%',
                padding: '8px 12px',
                border: '1px solid #cbd5e1',
                borderRadius: 6,
                fontSize: 14,
                boxSizing: 'border-box',
                marginBottom: 8,
              }}
            >
              <option value="">-- Select Topic --</option>
              {allTopics.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>

            <div style={{ display: 'flex', gap: 8 }}>
              {!streamConnected ? (
                <button
                  onClick={handleConnectStream}
                  disabled={!selectedTopic}
                  style={{
                    padding: '8px 18px',
                    backgroundColor: !selectedTopic ? '#94a3b8' : '#10b981',
                    color: '#fff',
                    border: 'none',
                    borderRadius: 6,
                    fontSize: 14,
                    fontWeight: 500,
                    cursor: !selectedTopic ? 'not-allowed' : 'pointer',
                  }}
                >
                  Connect
                </button>
              ) : (
                <button
                  onClick={handleDisconnectStream}
                  style={{
                    padding: '8px 18px',
                    backgroundColor: '#ef4444',
                    color: '#fff',
                    border: 'none',
                    borderRadius: 6,
                    fontSize: 14,
                    fontWeight: 500,
                    cursor: 'pointer',
                  }}
                >
                  Disconnect
                </button>
              )}
              <span style={{ fontSize: 13, color: streamConnected ? '#10b981' : '#94a3b8', alignSelf: 'center' }}>
                {streamConnected ? 'Streaming...' : 'Disconnected'}
              </span>
              {streamEvents.length > 0 && (
                <button
                  onClick={() => setStreamEvents([])}
                  style={{
                    marginLeft: 'auto',
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
              )}
            </div>
          </div>

          <div
            style={{
              backgroundColor: '#0f172a',
              borderRadius: 6,
              padding: 12,
              height: 300,
              overflow: 'auto',
              fontFamily: 'monospace',
              fontSize: 12,
            }}
          >
            {streamEvents.length === 0 ? (
              <span style={{ color: '#64748b' }}>Events will appear here...</span>
            ) : (
              streamEvents.map((evt, i) => (
                <div key={i} style={{ marginBottom: 4 }}>
                  <span style={{ color: '#64748b' }}>[{evt.timestamp}] </span>
                  {evt.topic && <span style={{ color: '#f59e0b' }}>{evt.topic}: </span>}
                  <span style={{ color: '#e2e8f0' }}>{evt.data}</span>
                </div>
              ))
            )}
            <div ref={streamEndRef} />
          </div>
        </div>

        {/* Publish Event */}
        <div style={sectionStyle}>
          <h2 style={{ fontSize: 16, fontWeight: 600, color: '#334155', marginTop: 0, marginBottom: 16 }}>
            Publish Event
          </h2>

          {publishError && <p style={{ color: '#dc2626', fontSize: 14, marginBottom: 12 }}>{publishError}</p>}
          {publishSuccess && <p style={{ color: '#10b981', fontSize: 14, marginBottom: 12 }}>{publishSuccess}</p>}

          <div style={{ marginBottom: 14 }}>
            <label style={{ display: 'block', fontSize: 13, color: '#64748b', marginBottom: 4 }}>Topic</label>
            <input
              type="text"
              value={publishTopic}
              onChange={(e) => setPublishTopic(e.target.value)}
              placeholder="e.g. orders.created"
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

          <div style={{ marginBottom: 14 }}>
            <label style={{ display: 'block', fontSize: 13, color: '#64748b', marginBottom: 4 }}>Event Body (JSON)</label>
            <textarea
              value={publishBody}
              onChange={(e) => setPublishBody(e.target.value)}
              rows={10}
              style={{
                width: '100%',
                padding: '8px 12px',
                border: '1px solid #cbd5e1',
                borderRadius: 6,
                fontSize: 13,
                fontFamily: 'monospace',
                resize: 'vertical',
                boxSizing: 'border-box',
              }}
            />
          </div>

          <button
            onClick={handlePublish}
            disabled={publishing || !publishTopic.trim()}
            style={{
              padding: '10px 24px',
              backgroundColor: publishing || !publishTopic.trim() ? '#94a3b8' : '#3b82f6',
              color: '#fff',
              border: 'none',
              borderRadius: 6,
              fontSize: 14,
              fontWeight: 600,
              cursor: publishing || !publishTopic.trim() ? 'not-allowed' : 'pointer',
            }}
          >
            {publishing ? 'Publishing...' : 'Publish Event'}
          </button>
        </div>
      </div>
    </div>
  );
}
