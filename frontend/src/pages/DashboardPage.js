import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getProfile } from '../api/users';
import { getProblemStats } from '../api/problems';
import LoadingSpinner from '../components/LoadingSpinner';

function StatCard({ label, value, sub, color = 'text-white' }) {
  return (
    <div className="card p-5">
      <p className={`text-3xl font-bold ${color}`}>{value}</p>
      <p className="text-sm text-gray-400 mt-1">{label}</p>
      {sub && <p className="text-xs text-gray-600 mt-0.5">{sub}</p>}
    </div>
  );
}

function ProgressBar({ label, solved, total, color }) {
  const pct = total > 0 ? Math.round((solved / total) * 100) : 0;
  return (
    <div>
      <div className="flex justify-between text-sm mb-1.5">
        <span className={`font-medium ${color}`}>{label}</span>
        <span className="text-gray-400">{solved} / {total}</span>
      </div>
      <div className="h-2 bg-gray-800 rounded-full overflow-hidden">
        <div
          className={`h-full rounded-full transition-all duration-700 ${color.replace('text-', 'bg-')}`}
          style={{ width: `${pct}%` }}
        />
      </div>
      <p className="text-xs text-gray-600 mt-1 text-right">{pct}%</p>
    </div>
  );
}

export default function DashboardPage() {
  const [profile, setProfile]   = useState(null);
  const [pStats,  setPStats]    = useState({});
  const [loading, setLoading]   = useState(true);

  useEffect(() => {
    Promise.all([getProfile(), getProblemStats()])
      .then(([profRes, statsRes]) => {
        setProfile(profRes.data);
        setPStats(statsRes.data);
      })
      .catch(err => console.error(err))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner text="Loading dashboard…" />;
  if (!profile) return <p className="text-center text-red-400 mt-12">Failed to load profile.</p>;

  const { stats } = profile;
  const acceptanceRate = stats.totalSubmissions > 0
    ? ((stats.acceptedSubmissions / stats.totalSubmissions) * 100).toFixed(1)
    : '0.0';

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

      {/* Profile header */}
      <div className="card p-6 mb-6 flex flex-col sm:flex-row items-start sm:items-center gap-5">
        <div className="h-16 w-16 rounded-full bg-gradient-to-br from-brand-400 to-orange-500 flex items-center justify-center text-2xl font-bold text-white flex-shrink-0">
          {profile.username[0].toUpperCase()}
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-2xl font-bold text-white">{profile.username}</h1>
            {profile.streak > 0 && (
              <span className="flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-orange-900/30 border border-orange-700/50 text-orange-400 text-xs font-semibold">
                🔥 {profile.streak} day streak
              </span>
            )}
          </div>
          <p className="text-gray-400 text-sm mt-1">{profile.email}</p>
          {profile.bio && <p className="text-gray-300 text-sm mt-2">{profile.bio}</p>}
          <p className="text-xs text-gray-600 mt-2">
            Member since {profile.createdAt ? new Date(profile.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'long' }) : '—'}
          </p>
        </div>
      </div>

      {/* Stats grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
        <StatCard
          label="Problems Solved"
          value={stats.solvedProblems}
          color="text-brand-400"
          sub={`of ${(pStats.TOTAL || 0)} total`}
        />
        <StatCard
          label="Total Submissions"
          value={stats.totalSubmissions}
          color="text-white"
        />
        <StatCard
          label="Acceptance Rate"
          value={`${acceptanceRate}%`}
          color="text-blue-400"
          sub={`${stats.acceptedSubmissions} accepted`}
        />
        <StatCard
          label="Day Streak"
          value={profile.streak}
          color="text-orange-400"
          sub="consecutive days"
        />
      </div>

      {/* Progress by difficulty */}
      <div className="card p-6 mb-6">
        <h2 className="text-lg font-semibold text-white mb-5">Progress by Difficulty</h2>
        <div className="space-y-5">
          <ProgressBar
            label="Easy"
            solved={stats.easySolved}
            total={pStats.EASY || 0}
            color="text-green-400"
          />
          <ProgressBar
            label="Medium"
            solved={stats.mediumSolved}
            total={pStats.MEDIUM || 0}
            color="text-yellow-400"
          />
          <ProgressBar
            label="Hard"
            solved={stats.hardSolved}
            total={pStats.HARD || 0}
            color="text-red-400"
          />
        </div>
      </div>

      {/* Quick actions */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Link to="/problems" className="card p-5 hover:border-brand-500/50 transition-colors group">
          <div className="text-2xl mb-2">📚</div>
          <h3 className="font-semibold text-white group-hover:text-brand-400 transition-colors">
            Browse Problems
          </h3>
          <p className="text-sm text-gray-400 mt-1">Find your next challenge</p>
        </Link>
        <Link to="/submissions" className="card p-5 hover:border-blue-500/50 transition-colors group">
          <div className="text-2xl mb-2">📝</div>
          <h3 className="font-semibold text-white group-hover:text-blue-400 transition-colors">
            View Submissions
          </h3>
          <p className="text-sm text-gray-400 mt-1">Review your submission history</p>
        </Link>
      </div>
    </div>
  );
}
