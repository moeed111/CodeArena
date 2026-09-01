import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { getMySubmissions } from '../api/submissions';
import StatusBadge from '../components/StatusBadge';
import LoadingSpinner from '../components/LoadingSpinner';

export default function SubmissionsPage() {
  const [submissions, setSubmissions] = useState([]);
  const [loading,     setLoading]     = useState(true);
  const [page,        setPage]        = useState(0);
  const [totalPages,  setTotalPages]  = useState(0);
  const [filter,      setFilter]      = useState('ALL');

  const STATUS_FILTERS = ['ALL', 'ACCEPTED', 'WRONG_ANSWER', 'COMPILE_ERROR', 'RUNTIME_ERROR', 'TIME_LIMIT_EXCEEDED'];

  const fetchSubmissions = useCallback(async (pg = 0) => {
    setLoading(true);
    try {
      const { data } = await getMySubmissions({ page: pg, size: 20 });
      setSubmissions(data.content);
      setTotalPages(data.totalPages);
      setPage(pg);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchSubmissions(0); }, [fetchSubmissions]);

  const filtered = filter === 'ALL'
    ? submissions
    : submissions.filter(s => s.status === filter);

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-white">Submission History</h1>
        <Link to="/problems" className="btn-primary text-sm">+ New Submission</Link>
      </div>

      {/* Status filter tabs */}
      <div className="flex flex-wrap gap-2 mb-5">
        {STATUS_FILTERS.map(s => (
          <button
            key={s}
            onClick={() => setFilter(s)}
            className={`px-3 py-1 rounded-full text-xs font-semibold border transition-colors ${
              filter === s
                ? 'bg-brand-500 border-brand-500 text-white'
                : 'border-gray-600 text-gray-400 hover:border-gray-400'
            }`}
          >
            {s === 'ALL' ? 'All' : s.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())}
          </button>
        ))}
      </div>

      <div className="card overflow-hidden">
        {/* Table header */}
        <div className="grid grid-cols-12 gap-2 px-5 py-3 border-b border-gray-700 text-xs font-semibold text-gray-500 uppercase tracking-wider">
          <div className="col-span-4">Problem</div>
          <div className="col-span-3">Status</div>
          <div className="col-span-2 text-center">Tests</div>
          <div className="col-span-2 text-center">Runtime</div>
          <div className="col-span-1 text-right">When</div>
        </div>

        {loading ? (
          <LoadingSpinner text="Loading submissions…" />
        ) : filtered.length === 0 ? (
          <div className="py-16 text-center text-gray-500">
            <div className="text-4xl mb-3">📭</div>
            <p>No submissions yet.</p>
            <Link to="/problems" className="btn-primary mt-4 inline-block">Start Solving</Link>
          </div>
        ) : (
          <div className="divide-y divide-gray-800">
            {filtered.map(s => (
              <div key={s.id} className="grid grid-cols-12 gap-2 px-5 py-3.5 hover:bg-white/5 transition-colors">
                <div className="col-span-4 self-center">
                  <Link
                    to={`/problems/${s.problemSlug}`}
                    className="text-sm font-medium text-gray-200 hover:text-brand-400 transition-colors line-clamp-1"
                  >
                    {s.problemTitle}
                  </Link>
                  <p className="text-xs text-gray-600 mt-0.5">{s.language}</p>
                </div>
                <div className="col-span-3 self-center">
                  <StatusBadge status={s.status} />
                </div>
                <div className="col-span-2 self-center text-center text-sm">
                  {s.totalTests > 0 ? (
                    <span className={s.passedTests === s.totalTests ? 'text-green-400' : 'text-gray-400'}>
                      {s.passedTests}/{s.totalTests}
                    </span>
                  ) : (
                    <span className="text-gray-600">—</span>
                  )}
                </div>
                <div className="col-span-2 self-center text-center text-sm text-gray-400">
                  {s.runtimeMs != null ? `${s.runtimeMs}ms` : '—'}
                </div>
                <div className="col-span-1 self-center text-right text-xs text-gray-500">
                  {s.submittedAt
                    ? timeAgo(new Date(s.submittedAt))
                    : '—'}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-5">
          <button
            onClick={() => fetchSubmissions(page - 1)}
            disabled={page === 0}
            className="btn-secondary px-3 py-1 text-sm disabled:opacity-30"
          >← Prev</button>
          <span className="px-3 py-1 text-sm text-gray-400 self-center">
            Page {page + 1} of {totalPages}
          </span>
          <button
            onClick={() => fetchSubmissions(page + 1)}
            disabled={page >= totalPages - 1}
            className="btn-secondary px-3 py-1 text-sm disabled:opacity-30"
          >Next →</button>
        </div>
      )}
    </div>
  );
}

function timeAgo(date) {
  const seconds = Math.floor((new Date() - date) / 1000);
  if (seconds < 60)   return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400)return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}
