import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import CodeMirror from '@uiw/react-codemirror';
import { java } from '@codemirror/lang-java';
import { oneDark } from '@codemirror/theme-one-dark';
import { getProblem } from '../api/problems';
import { submitCode, runCode, getProblemSubmissions } from '../api/submissions';
import { useAuth } from '../context/AuthContext';
import DifficultyBadge from '../components/DifficultyBadge';
import StatusBadge from '../components/StatusBadge';
import LoadingSpinner from '../components/LoadingSpinner';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

const TABS = ['Description', 'Submissions', 'Discussion'];

export default function ProblemDetailPage() {
  const { slug } = useParams();
  const { user }  = useAuth();
  const navigate  = useNavigate();

  const [problem,    setProblem]    = useState(null);
  const [code,       setCode]       = useState('');
  const [activeTab,  setActiveTab]  = useState('Description');
  const [panelTab,   setPanelTab]   = useState('output'); // output | testcases
  const [submitting, setSubmitting] = useState(false);
  const [running,    setRunning]    = useState(false);
  const [result,     setResult]     = useState(null);
  const [runResult,  setRunResult]  = useState(null);
  const [submissions, setSubmissions] = useState([]);
  const [subLoading,  setSubLoading]  = useState(false);
  const [loading,    setLoading]    = useState(true);
  const [error,      setError]      = useState('');
  const [leftWidth,  setLeftWidth]  = useState(45); // percent
  const [dragging,   setDragging]   = useState(false);

  useEffect(() => {
    setLoading(true);
    getProblem(slug)
      .then(({ data }) => {
        setProblem(data);
        setCode(data.starterCode || '');
      })
      .catch(() => setError('Problem not found'))
      .finally(() => setLoading(false));
  }, [slug]);

  const fetchSubmissions = useCallback(() => {
    if (!user || !problem) return;
    setSubLoading(true);
    getProblemSubmissions(problem.id, { page: 0, size: 10 })
      .then(({ data }) => setSubmissions(data.content))
      .catch(() => {})
      .finally(() => setSubLoading(false));
  }, [user, problem]);

  useEffect(() => {
    if (activeTab === 'Submissions') fetchSubmissions();
  }, [activeTab, fetchSubmissions]);

  // Drag-to-resize panel
  const onMouseDown = () => setDragging(true);
  useEffect(() => {
    if (!dragging) return;
    const onMove = e => {
      const pct = (e.clientX / window.innerWidth) * 100;
      setLeftWidth(Math.max(25, Math.min(70, pct)));
    };
    const onUp = () => setDragging(false);
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => { window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp); };
  }, [dragging]);

  const handleRun = async () => {
    if (!user) { navigate('/login'); return; }
    setRunning(true); setRunResult(null); setPanelTab('output');
    try {
      const { data } = await runCode({ code, language: 'java', problemId: problem.id });
      setRunResult(data);
    } catch (err) {
      setRunResult({ status: 'ERROR', errorMessage: err.response?.data?.message || 'Execution failed' });
    } finally {
      setRunning(false);
    }
  };

  const handleSubmit = async () => {
    if (!user) { navigate('/login'); return; }
    setSubmitting(true); setResult(null); setPanelTab('output');
    try {
      const { data } = await submitCode({ code, language: 'java', problemId: problem.id });
      setResult(data);
      if (activeTab === 'Submissions') fetchSubmissions();
    } catch (err) {
      setResult({ status: 'ERROR', errorMessage: err.response?.data?.message || 'Submission failed' });
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <div className="h-[calc(100vh-56px)] flex items-center justify-center"><LoadingSpinner text="Loading problem…" /></div>;
  if (error)   return (
    <div className="flex flex-col items-center justify-center h-64 gap-4">
      <p className="text-red-400 text-lg">{error}</p>
      <Link to="/problems" className="btn-primary">← Back to Problems</Link>
    </div>
  );

  return (
    <div className="h-[calc(100vh-56px)] flex overflow-hidden select-none">

      {/* LEFT PANEL – Problem info */}
      <div style={{ width: `${leftWidth}%` }} className="flex flex-col overflow-hidden border-r border-gray-700">

        {/* Tabs */}
        <div className="flex border-b border-gray-700 bg-[#0f0f23]">
          {TABS.map(tab => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`px-4 py-3 text-sm font-medium transition-colors border-b-2 ${
                activeTab === tab
                  ? 'border-brand-500 text-brand-400'
                  : 'border-transparent text-gray-400 hover:text-gray-200'
              }`}
            >
              {tab}
            </button>
          ))}
        </div>

        {/* Tab content */}
        <div className="flex-1 overflow-y-auto p-6">
          {activeTab === 'Description' && (
            <div>
              <div className="flex items-center gap-3 mb-4">
                <h1 className="text-xl font-bold text-white">{problem.title}</h1>
                <DifficultyBadge difficulty={problem.difficulty} />
                {problem.solved && <span className="badge-easy">✓ Solved</span>}
              </div>

              {/* Tags */}
              <div className="flex flex-wrap gap-1.5 mb-5">
                {problem.tags && [...problem.tags].map(tag => (
                  <span key={tag} className="px-2 py-0.5 text-xs rounded border border-gray-700 text-gray-400">
                    {tag}
                  </span>
                ))}
              </div>

              {/* Description */}
              <div className="prose prose-invert prose-sm max-w-none mb-6">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {problem.description}
                </ReactMarkdown>
              </div>

              {/* Examples */}
              {problem.examples?.length > 0 && (
                <div className="space-y-4 mb-6">
                  {problem.examples.map((ex, i) => (
                    <div key={ex.id} className="bg-[#0f0f23] rounded-lg p-4 border border-gray-700">
                      <p className="text-sm font-semibold text-gray-300 mb-2">Example {i + 1}</p>
                      <div className="space-y-1.5 font-mono text-sm">
                        <div><span className="text-gray-500">Input: </span><span className="text-gray-200">{ex.input}</span></div>
                        <div><span className="text-gray-500">Output: </span><span className="text-gray-200">{ex.output}</span></div>
                        {ex.explanation && (
                          <div className="mt-2 text-gray-400 font-sans text-xs">
                            <span className="text-gray-500">Explanation: </span>{ex.explanation}
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Constraints */}
              {problem.constraints && (
                <div className="mb-4">
                  <h3 className="text-sm font-semibold text-gray-300 mb-2">Constraints</h3>
                  <div className="bg-[#0f0f23] rounded-lg p-4 border border-gray-700">
                    <pre className="text-xs text-gray-400 whitespace-pre-wrap font-mono">{problem.constraints}</pre>
                  </div>
                </div>
              )}

              {/* Stats */}
              <div className="flex gap-6 text-xs text-gray-500 border-t border-gray-700 pt-4">
                <span>Submissions: <b className="text-gray-300">{problem.submissions?.toLocaleString()}</b></span>
                <span>Acceptance: <b className="text-gray-300">{problem.acceptance?.toFixed(1)}%</b></span>
              </div>
            </div>
          )}

          {activeTab === 'Submissions' && (
            <div>
              <h2 className="text-lg font-semibold text-white mb-4">My Submissions</h2>
              {!user ? (
                <div className="text-center py-8">
                  <p className="text-gray-400 mb-3">Sign in to view your submissions</p>
                  <Link to="/login" className="btn-primary">Sign In</Link>
                </div>
              ) : subLoading ? <LoadingSpinner /> : submissions.length === 0 ? (
                <p className="text-gray-400 text-sm">No submissions yet. Give it a try!</p>
              ) : (
                <div className="space-y-2">
                  {submissions.map(s => (
                    <div key={s.id} className="bg-[#0f0f23] rounded-lg p-3 border border-gray-700 flex justify-between items-center">
                      <div className="flex items-center gap-3">
                        <StatusBadge status={s.status} />
                        <span className="text-xs text-gray-400">
                          {s.passedTests}/{s.totalTests} tests
                        </span>
                      </div>
                      <div className="text-right">
                        {s.runtimeMs && <span className="text-xs text-gray-400">{s.runtimeMs}ms</span>}
                        <p className="text-xs text-gray-600 mt-0.5">
                          {s.submittedAt ? new Date(s.submittedAt).toLocaleString() : ''}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {activeTab === 'Discussion' && (
            <div className="text-center py-16 text-gray-500">
              <div className="text-4xl mb-3">💬</div>
              <p>Discussion coming soon!</p>
            </div>
          )}
        </div>
      </div>

      {/* DRAG HANDLE */}
      <div
        onMouseDown={onMouseDown}
        className={`w-1 cursor-col-resize flex-shrink-0 transition-colors ${dragging ? 'bg-brand-500' : 'bg-gray-700 hover:bg-brand-500/50'}`}
      />

      {/* RIGHT PANEL – Editor + Output */}
      <div style={{ width: `${100 - leftWidth}%` }} className="flex flex-col overflow-hidden">

        {/* Editor toolbar */}
        <div className="flex items-center justify-between px-4 py-2 bg-[#0f0f23] border-b border-gray-700">
          <div className="flex items-center gap-2">
            <span className="px-2 py-1 text-xs font-mono bg-[#1a1a2e] border border-gray-700 rounded text-blue-300">Java</span>
          </div>
          <div className="flex gap-2">
            <button
              onClick={handleRun}
              disabled={running || submitting}
              className="btn-secondary py-1.5 px-4 text-sm flex items-center gap-1.5"
            >
              {running ? (
                <span className="h-3 w-3 animate-spin rounded-full border border-gray-400 border-t-white" />
              ) : '▶'}
              Run
            </button>
            <button
              onClick={handleSubmit}
              disabled={running || submitting}
              className="btn-primary py-1.5 px-4 text-sm flex items-center gap-1.5"
            >
              {submitting ? (
                <span className="h-3 w-3 animate-spin rounded-full border border-white/30 border-t-white" />
              ) : '⬆'}
              Submit
            </button>
          </div>
        </div>

        {/* Code editor */}
        <div className="flex-1 overflow-auto">
          <CodeMirror
            value={code}
            height="100%"
            extensions={[java()]}
            theme={oneDark}
            onChange={setCode}
            basicSetup={{
              lineNumbers: true,
              foldGutter: true,
              autocompletion: true,
              bracketMatching: true,
              indentOnInput: true,
            }}
          />
        </div>

        {/* Output panel */}
        <div className="h-56 border-t border-gray-700 flex flex-col bg-[#0d0d1a]">
          <div className="flex border-b border-gray-700">
            {['output', 'testcases'].map(t => (
              <button
                key={t}
                onClick={() => setPanelTab(t)}
                className={`px-4 py-2 text-xs font-medium capitalize transition-colors border-b-2 ${
                  panelTab === t
                    ? 'border-brand-500 text-brand-400'
                    : 'border-transparent text-gray-500 hover:text-gray-300'
                }`}
              >
                {t === 'output' ? 'Console Output' : 'Test Cases'}
              </button>
            ))}
          </div>

          <div className="flex-1 overflow-y-auto p-4">
            {panelTab === 'testcases' && (
              <div className="space-y-2">
                {problem.visibleTestCases?.length > 0 ? (
                  problem.visibleTestCases.map((tc, i) => (
                    <div key={tc.id} className="bg-[#0f0f23] rounded p-3 border border-gray-700">
                      <p className="text-xs text-gray-500 mb-1">Case {i + 1}</p>
                      <div className="font-mono text-xs space-y-1">
                        <div><span className="text-gray-500">Input: </span><span className="text-gray-300">{tc.input}</span></div>
                        <div><span className="text-gray-500">Expected: </span><span className="text-gray-300">{tc.expected}</span></div>
                      </div>
                    </div>
                  ))
                ) : (
                  <p className="text-gray-500 text-xs">No visible test cases.</p>
                )}
              </div>
            )}

            {panelTab === 'output' && (
              <div>
                {/* Run result */}
                {runResult && !result && (
                  <RunResultPanel result={runResult} type="run" />
                )}
                {/* Submit result */}
                {result && (
                  <RunResultPanel result={result} type="submit" />
                )}
                {/* Idle */}
                {!runResult && !result && !running && !submitting && (
                  <p className="text-gray-600 text-xs">Click "Run" to test your code, or "Submit" to judge against all test cases.</p>
                )}
                {(running || submitting) && (
                  <div className="flex items-center gap-2 text-gray-400 text-sm">
                    <span className="h-4 w-4 animate-spin rounded-full border-2 border-gray-600 border-t-brand-400" />
                    {submitting ? 'Judging against all test cases…' : 'Running your code…'}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function RunResultPanel({ result, type }) {
  const isAccepted = result.status === 'ACCEPTED' || result.status === 'SUCCESS';
  const allPassed  = result.results?.every(r => r.passed);

  return (
    <div className="space-y-3">
      {/* Status header */}
      <div className={`flex items-center gap-2 font-semibold text-sm ${
        isAccepted || allPassed ? 'text-green-400' : 'text-red-400'
      }`}>
        <span>{isAccepted || allPassed ? '✓' : '✕'}</span>
        <span>
          {type === 'submit'
            ? (isAccepted ? 'Accepted' : (result.status || 'Wrong Answer'))
            : (allPassed ? 'All tests passed' : 'Some tests failed')}
        </span>
        {result.runtimeMs > 0 && (
          <span className="text-gray-500 font-normal ml-2 text-xs">Runtime: {result.runtimeMs}ms</span>
        )}
        {result.passedTests !== undefined && (
          <span className="text-gray-500 font-normal text-xs">
            ({result.passedTests}/{result.totalTests} passed)
          </span>
        )}
      </div>

      {/* Error message */}
      {result.errorMessage && (
        <pre className="text-xs text-red-400 bg-red-900/20 border border-red-800/50 rounded p-3 whitespace-pre-wrap overflow-x-auto font-mono">
          {result.errorMessage}
        </pre>
      )}

      {/* Test case results */}
      {result.results?.length > 0 && (
        <div className="space-y-2">
          {result.results.slice(0, 6).map((r, i) => (
            <div key={i} className={`rounded p-2.5 border text-xs font-mono ${
              r.passed
                ? 'bg-green-900/20 border-green-800/40'
                : 'bg-red-900/20 border-red-800/40'
            }`}>
              <div className="flex items-center gap-2 mb-1">
                <span className={r.passed ? 'text-green-400' : 'text-red-400'}>
                  {r.passed ? '✓' : '✕'} Case {r.index + 1}
                </span>
                {r.hidden && <span className="text-gray-500">(hidden)</span>}
                <span className="text-gray-600">{r.runtimeMs}ms</span>
              </div>
              {!r.hidden && r.input && (
                <div className="space-y-0.5 text-gray-300">
                  <div><span className="text-gray-500">Input:    </span>{r.input}</div>
                  <div><span className="text-gray-500">Expected: </span>{r.expected}</div>
                  {!r.passed && <div><span className="text-gray-500">Got:      </span><span className="text-red-300">{r.actual}</span></div>}
                </div>
              )}
              {r.errorMessage && (
                <div className="text-orange-400 mt-1">{r.errorMessage}</div>
              )}
            </div>
          ))}
          {result.results.length > 6 && (
            <p className="text-xs text-gray-500">… and {result.results.length - 6} more test cases</p>
          )}
        </div>
      )}
    </div>
  );
}
