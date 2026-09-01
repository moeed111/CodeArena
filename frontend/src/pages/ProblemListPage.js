import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { getProblems, getAllTags, getProblemStats } from '../api/problems';
import { useAuth } from '../context/AuthContext';
import DifficultyBadge from '../components/DifficultyBadge';
import StatusBadge from '../components/StatusBadge';
import LoadingSpinner from '../components/LoadingSpinner';

const DIFFICULTIES = ['ALL', 'EASY', 'MEDIUM', 'HARD'];

export default function ProblemListPage() {
  const { user } = useAuth();
  const [problems,    setProblems]    = useState([]);
  const [tags,        setTags]        = useState([]);
  const [stats,       setStats]       = useState({});
  const [loading,     setLoading]     = useState(true);
  const [page,        setPage]        = useState(0);
  const [totalPages,  setTotalPages]  = useState(0);
  const [totalItems,  setTotalItems]  = useState(0);
  const [filters, setFilters] = useState({
    difficulty: '', tag: '', search: ''
  });
  const [searchInput, setSearchInput] = useState('');

  const fetchProblems = useCallback(async (pg = 0) => {
    setLoading(true);
    try {
      const params = { page: pg, size: 20 };
      if (filters.difficulty) params.difficulty = filters.difficulty;
      if (filters.tag)        params.tag        = filters.tag;
      if (filters.search)     params.search     = filters.search;
      const { data } = await getProblems(params);
      setProblems(data.content);
      setTotalPages(data.totalPages);
      setTotalItems(data.totalElements);
      setPage(pg);
    } catch (err) {
      console.error('Failed to fetch problems', err);
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => { fetchProblems(0); }, [fetchProblems]);

  useEffect(() => {
    getAllTags().then(r => setTags(r.data)).catch(() => {});
    getProblemStats().then(r => setStats(r.data)).catch(() => {});
  }, []);

  const handleSearch = e => {
    e.preventDefault();
    setFilters(f => ({ ...f, search: searchInput }));
  };

  const setFilter = (key, val) =>
    setFilters(f => ({ ...f, [key]: f[key] === val ? '' : val }));

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

      {/* Stats row */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
        {[
          { label: 'Total',  value: stats.TOTAL  || 0, color: 'text-white'        },
          { label: 'Easy',   value: stats.EASY   || 0, color: 'text-green-400'    },
          { label: 'Medium', value: stats.MEDIUM || 0, color: 'text-yellow-400'   },
          { label: 'Hard',   value: stats.HARD   || 0, color: 'text-red-400'      },
        ].map(s => (
          <div key={s.label} className="card p-4 text-center">
            <p className={`text-2xl font-bold ${s.color}`}>{s.value}</p>
            <p className="text-xs text-gray-400 mt-1">{s.label}</p>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="card p-4 mb-6 space-y-4">
        {/* Search */}
        <form onSubmit={handleSearch} className="flex gap-2">
          <input
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            placeholder="Search problems…"
            className="input flex-1"
          />
          <button type="submit" className="btn-primary px-5">Search</button>
          {filters.search && (
            <button type="button" onClick={() => { setSearchInput(''); setFilters(f => ({ ...f, search: '' })); }}
              className="btn-secondary px-3">✕</button>
          )}
        </form>

        {/* Difficulty filter */}
        <div className="flex flex-wrap gap-2">
          {DIFFICULTIES.map(d => (
            <button
              key={d}
              onClick={() => setFilter('difficulty', d === 'ALL' ? '' : d)}
              className={`px-3 py-1 rounded-full text-xs font-semibold border transition-colors ${
                (d === 'ALL' && !filters.difficulty) || filters.difficulty === d
                  ? 'bg-brand-500 border-brand-500 text-white'
                  : 'border-gray-600 text-gray-400 hover:border-gray-400'
              }`}
            >
              {d}
            </button>
          ))}
        </div>

        {/* Tag filter */}
        <div className="flex flex-wrap gap-2 max-h-20 overflow-y-auto">
          {tags.map(tag => (
            <button
              key={tag}
              onClick={() => setFilter('tag', tag)}
              className={`px-2.5 py-0.5 rounded text-xs border transition-colors ${
                filters.tag === tag
                  ? 'bg-purple-700/50 border-purple-500 text-purple-200'
                  : 'border-gray-700 text-gray-400 hover:border-gray-500'
              }`}
            >
              {tag}
            </button>
          ))}
        </div>
      </div>

      {/* Problem table */}
      <div className="card overflow-hidden">
        {/* Header */}
        <div className="grid grid-cols-12 gap-4 px-6 py-3 border-b border-gray-700 text-xs font-semibold text-gray-500 uppercase tracking-wider">
          <div className="col-span-1">#</div>
          <div className="col-span-6">Title</div>
          <div className="col-span-2">Difficulty</div>
          <div className="col-span-2">Acceptance</div>
          <div className="col-span-1 text-center">{user ? '✓' : ''}</div>
        </div>

        {loading ? (
          <LoadingSpinner text="Loading problems…" />
        ) : problems.length === 0 ? (
          <div className="py-16 text-center text-gray-500">
            <div className="text-4xl mb-3">🔍</div>
            <p>No problems found matching your filters.</p>
          </div>
        ) : (
          <div className="divide-y divide-gray-800">
            {problems.map((p, idx) => (
              <div
                key={p.id}
                className="grid grid-cols-12 gap-4 px-6 py-3.5 hover:bg-white/5 transition-colors group"
              >
                <div className="col-span-1 text-sm text-gray-500 self-center">
                  {page * 20 + idx + 1}
                </div>
                <div className="col-span-6 self-center">
                  <Link
                    to={`/problems/${p.slug}`}
                    className="text-sm font-medium text-gray-200 group-hover:text-brand-400 transition-colors"
                  >
                    {p.title}
                  </Link>
                  <div className="flex flex-wrap gap-1 mt-1">
                    {p.tags && [...p.tags].slice(0, 3).map(tag => (
                      <span key={tag}
                        className="px-1.5 py-0 text-xs text-gray-500 border border-gray-700 rounded"
                      >{tag}</span>
                    ))}
                  </div>
                </div>
                <div className="col-span-2 self-center">
                  <DifficultyBadge difficulty={p.difficulty} />
                </div>
                <div className="col-span-2 self-center text-sm text-gray-400">
                  {p.acceptance?.toFixed(1)}%
                </div>
                <div className="col-span-1 self-center text-center">
                  {user && (
                    p.solved
                      ? <span className="text-green-400 text-sm">✓</span>
                      : <span className="text-gray-700 text-sm">–</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <p className="text-sm text-gray-400">
            Showing {page * 20 + 1}–{Math.min((page + 1) * 20, totalItems)} of {totalItems}
          </p>
          <div className="flex gap-2">
            <button
              onClick={() => fetchProblems(page - 1)}
              disabled={page === 0}
              className="btn-secondary px-3 py-1 text-sm disabled:opacity-30"
            >← Prev</button>
            {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
              const pg = Math.max(0, Math.min(page - 2 + i, totalPages - 1));
              return (
                <button key={pg}
                  onClick={() => fetchProblems(pg)}
                  className={`px-3 py-1 rounded-lg text-sm border transition-colors ${
                    pg === page
                      ? 'bg-brand-500 border-brand-500 text-white'
                      : 'border-gray-600 text-gray-400 hover:border-gray-400'
                  }`}
                >{pg + 1}</button>
              );
            })}
            <button
              onClick={() => fetchProblems(page + 1)}
              disabled={page >= totalPages - 1}
              className="btn-secondary px-3 py-1 text-sm disabled:opacity-30"
            >Next →</button>
          </div>
        </div>
      )}
    </div>
  );
}
