'use client';

import React, { useState } from 'react';
import { apiClient } from '@gateway/shared-ui/lib/api-client';

interface DetectedIssue {
  id?: string;
  title: string;
  description: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
  location?: string;
}

export default function AiAssistantPage() {
  // Generate API Spec
  const [specDescription, setSpecDescription] = useState('');
  const [generatedSpec, setGeneratedSpec] = useState('');
  const [generatingSpec, setGeneratingSpec] = useState(false);
  const [specError, setSpecError] = useState('');
  const [specCopied, setSpecCopied] = useState(false);

  // Suggest Policy
  const [policyDescription, setPolicyDescription] = useState('');
  const [generatedPolicy, setGeneratedPolicy] = useState('');
  const [generatingPolicy, setGeneratingPolicy] = useState(false);
  const [policyError, setPolicyError] = useState('');
  const [policyCopied, setPolicyCopied] = useState(false);

  // Analyze Spec
  const [analyzeInput, setAnalyzeInput] = useState('');
  const [issues, setIssues] = useState<DetectedIssue[]>([]);
  const [analyzing, setAnalyzing] = useState(false);
  const [analyzeError, setAnalyzeError] = useState('');

  const handleGenerateSpec = async () => {
    if (!specDescription.trim()) return;
    setGeneratingSpec(true);
    setSpecError('');
    setGeneratedSpec('');
    try {
      const data = await apiClient<{ spec: string }>('/v1/ai/assistant/generate-spec', {
        method: 'POST',
        body: JSON.stringify({ description: specDescription }),
      });
      setGeneratedSpec(typeof data === 'string' ? data : data.spec || JSON.stringify(data, null, 2));
    } catch (err: unknown) {
      setSpecError(err instanceof Error ? err.message : 'Failed to generate spec');
    } finally {
      setGeneratingSpec(false);
    }
  };

  const handleSuggestPolicy = async () => {
    if (!policyDescription.trim()) return;
    setGeneratingPolicy(true);
    setPolicyError('');
    setGeneratedPolicy('');
    try {
      const data = await apiClient<{ policy: string }>('/v1/ai/assistant/suggest-policy', {
        method: 'POST',
        body: JSON.stringify({ description: policyDescription }),
      });
      const result = typeof data === 'string' ? data : data.policy || JSON.stringify(data, null, 2);
      // Pretty-print if JSON
      try {
        setGeneratedPolicy(JSON.stringify(JSON.parse(result), null, 2));
      } catch {
        setGeneratedPolicy(result);
      }
    } catch (err: unknown) {
      setPolicyError(err instanceof Error ? err.message : 'Failed to suggest policy');
    } finally {
      setGeneratingPolicy(false);
    }
  };

  const handleAnalyzeSpec = async () => {
    if (!analyzeInput.trim()) return;
    setAnalyzing(true);
    setAnalyzeError('');
    setIssues([]);
    try {
      const data = await apiClient<{ issues: DetectedIssue[] }>('/v1/ai/assistant/detect-issues', {
        method: 'POST',
        body: JSON.stringify({ spec: analyzeInput }),
      });
      setIssues(Array.isArray(data) ? data as unknown as DetectedIssue[] : data.issues ?? []);
    } catch (err: unknown) {
      setAnalyzeError(err instanceof Error ? err.message : 'Failed to analyze spec');
    } finally {
      setAnalyzing(false);
    }
  };

  const copyToClipboard = (text: string, setCopied: (v: boolean) => void) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const getSeverityStyle = (severity: string): React.CSSProperties => {
    switch (severity) {
      case 'CRITICAL':
        return { backgroundColor: '#fee2e2', color: '#991b1b' };
      case 'HIGH':
        return { backgroundColor: '#fee2e2', color: '#991b1b' };
      case 'MEDIUM':
        return { backgroundColor: '#fef3c7', color: '#92400e' };
      case 'LOW':
        return { backgroundColor: '#dbeafe', color: '#1e40af' };
      case 'INFO':
      default:
        return { backgroundColor: '#f1f5f9', color: '#475569' };
    }
  };

  const sectionStyle: React.CSSProperties = {
    backgroundColor: '#fff',
    borderRadius: 8,
    border: '1px solid #e2e8f0',
    padding: 24,
    marginBottom: 20,
  };

  const textareaStyle: React.CSSProperties = {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #cbd5e1',
    borderRadius: 6,
    fontSize: 14,
    resize: 'vertical',
    boxSizing: 'border-box',
    fontFamily: 'system-ui, sans-serif',
  };

  const monoTextareaStyle: React.CSSProperties = {
    ...textareaStyle,
    fontFamily: 'monospace',
    fontSize: 13,
    backgroundColor: '#0f172a',
    color: '#e2e8f0',
    border: '1px solid #334155',
  };

  const buttonStyle = (disabled: boolean): React.CSSProperties => ({
    padding: '8px 20px',
    backgroundColor: disabled ? '#94a3b8' : '#3b82f6',
    color: '#fff',
    border: 'none',
    borderRadius: 6,
    fontSize: 14,
    fontWeight: 500,
    cursor: disabled ? 'not-allowed' : 'pointer',
  });

  return (
    <div style={{ maxWidth: 800 }}>
      <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', marginBottom: 24 }}>AI Assistant</h1>

      {/* Generate API Spec */}
      <div style={sectionStyle}>
        <h2 style={{ fontSize: 16, fontWeight: 600, color: '#334155', marginTop: 0, marginBottom: 16 }}>
          Generate API Spec
        </h2>
        <p style={{ fontSize: 13, color: '#64748b', marginBottom: 12 }}>
          Describe your API and the assistant will generate an OpenAPI specification.
        </p>

        {specError && <p style={{ color: '#dc2626', fontSize: 14, marginBottom: 12 }}>{specError}</p>}

        <textarea
          value={specDescription}
          onChange={(e) => setSpecDescription(e.target.value)}
          placeholder="Describe your API... e.g. A user management API with endpoints for CRUD operations, authentication, and role management."
          rows={4}
          style={textareaStyle}
        />
        <div style={{ marginTop: 12 }}>
          <button
            onClick={handleGenerateSpec}
            disabled={generatingSpec || !specDescription.trim()}
            style={buttonStyle(generatingSpec || !specDescription.trim())}
          >
            {generatingSpec ? 'Generating...' : 'Generate'}
          </button>
        </div>

        {generatedSpec && (
          <div style={{ marginTop: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Generated OpenAPI Spec</span>
              <button
                onClick={() => copyToClipboard(generatedSpec, setSpecCopied)}
                style={{
                  padding: '4px 10px',
                  fontSize: 12,
                  backgroundColor: specCopied ? '#3b82f6' : '#f1f5f9',
                  color: specCopied ? '#fff' : '#475569',
                  border: specCopied ? 'none' : '1px solid #cbd5e1',
                  borderRadius: 4,
                  cursor: 'pointer',
                }}
              >
                {specCopied ? 'Copied!' : 'Copy'}
              </button>
            </div>
            <textarea
              readOnly
              value={generatedSpec}
              rows={14}
              style={monoTextareaStyle}
            />
          </div>
        )}
      </div>

      {/* Suggest Policy */}
      <div style={sectionStyle}>
        <h2 style={{ fontSize: 16, fontWeight: 600, color: '#334155', marginTop: 0, marginBottom: 16 }}>
          Suggest Policy
        </h2>
        <p style={{ fontSize: 13, color: '#64748b', marginBottom: 12 }}>
          Describe your requirements and the assistant will suggest a gateway policy configuration.
        </p>

        {policyError && <p style={{ color: '#dc2626', fontSize: 14, marginBottom: 12 }}>{policyError}</p>}

        <textarea
          value={policyDescription}
          onChange={(e) => setPolicyDescription(e.target.value)}
          placeholder="Describe your policy needs... e.g. Rate limit to 100 requests per minute per API key, with a burst allowance of 20."
          rows={4}
          style={textareaStyle}
        />
        <div style={{ marginTop: 12 }}>
          <button
            onClick={handleSuggestPolicy}
            disabled={generatingPolicy || !policyDescription.trim()}
            style={buttonStyle(generatingPolicy || !policyDescription.trim())}
          >
            {generatingPolicy ? 'Suggesting...' : 'Suggest'}
          </button>
        </div>

        {generatedPolicy && (
          <div style={{ marginTop: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Suggested Policy</span>
              <button
                onClick={() => copyToClipboard(generatedPolicy, setPolicyCopied)}
                style={{
                  padding: '4px 10px',
                  fontSize: 12,
                  backgroundColor: policyCopied ? '#3b82f6' : '#f1f5f9',
                  color: policyCopied ? '#fff' : '#475569',
                  border: policyCopied ? 'none' : '1px solid #cbd5e1',
                  borderRadius: 4,
                  cursor: 'pointer',
                }}
              >
                {policyCopied ? 'Copied!' : 'Copy'}
              </button>
            </div>
            <textarea
              readOnly
              value={generatedPolicy}
              rows={12}
              style={monoTextareaStyle}
            />
          </div>
        )}
      </div>

      {/* Analyze Spec */}
      <div style={sectionStyle}>
        <h2 style={{ fontSize: 16, fontWeight: 600, color: '#334155', marginTop: 0, marginBottom: 16 }}>
          Analyze Spec
        </h2>
        <p style={{ fontSize: 13, color: '#64748b', marginBottom: 12 }}>
          Paste an OpenAPI spec and the assistant will detect issues and suggest improvements.
        </p>

        {analyzeError && <p style={{ color: '#dc2626', fontSize: 14, marginBottom: 12 }}>{analyzeError}</p>}

        <textarea
          value={analyzeInput}
          onChange={(e) => setAnalyzeInput(e.target.value)}
          placeholder="Paste your OpenAPI spec here (YAML or JSON)..."
          rows={8}
          style={{ ...textareaStyle, fontFamily: 'monospace', fontSize: 13 }}
        />
        <div style={{ marginTop: 12 }}>
          <button
            onClick={handleAnalyzeSpec}
            disabled={analyzing || !analyzeInput.trim()}
            style={buttonStyle(analyzing || !analyzeInput.trim())}
          >
            {analyzing ? 'Analyzing...' : 'Analyze'}
          </button>
        </div>

        {issues.length > 0 && (
          <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
              Detected Issues ({issues.length})
            </span>
            {issues.map((issue, i) => (
              <div
                key={issue.id || i}
                style={{
                  padding: '12px 16px',
                  backgroundColor: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: 6,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                  <span
                    style={{
                      display: 'inline-block',
                      padding: '2px 10px',
                      borderRadius: 9999,
                      fontSize: 11,
                      fontWeight: 600,
                      ...getSeverityStyle(issue.severity),
                    }}
                  >
                    {issue.severity}
                  </span>
                  <span style={{ fontSize: 14, fontWeight: 600, color: '#0f172a' }}>{issue.title}</span>
                </div>
                <p style={{ fontSize: 13, color: '#64748b', margin: 0 }}>{issue.description}</p>
                {issue.location && (
                  <code style={{ fontSize: 12, color: '#94a3b8', marginTop: 4, display: 'block' }}>
                    {issue.location}
                  </code>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
