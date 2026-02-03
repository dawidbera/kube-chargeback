import { useState, useEffect } from 'react'
import { LayoutDashboard, ShieldAlert, Wallet, PieChart as PieChartIcon, Activity } from 'lucide-react'
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend } from 'recharts'

interface Allocation {
  groupKey: string;
  totalCostUnits: number;
  cpuMcpu: number;
  memMib: number;
}

interface Compliance {
  summary: {
    ok: number;
    missingRequests: number;
    missingLimits: number;
    bothMissing: number;
  };
}

const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884d8'];

function App() {
  const [allocations, setAllocations] = useState<Allocation[]>([]);
  const [compliance, setCompliance] = useState<Compliance | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const now = new Date();
        const start = new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString();
        const end = now.toISOString();

        const [allocRes, compRes] = await Promise.all([
          fetch(`/api/v1/reports/allocations?from=${start}&to=${end}&groupBy=namespace`),
          fetch(`/api/v1/reports/compliance?from=${start}&to=${end}`)
        ]);

        if (allocRes.ok) {
          const data = await allocRes.json();
          setAllocations(data);
        } else {
          throw new Error("Allocations API failed");
        }

        if (compRes.ok) {
          const data = await compRes.json();
          setCompliance(data);
        } else {
          throw new Error("Compliance API failed");
        }
      } catch (error) {
        console.error("Failed to fetch data, using mock data", error);
        // Mock data for preview
        setAllocations([
          { groupKey: 'kube-system', totalCostUnits: 12.5, cpuMcpu: 2000, memMib: 4096 },
          { groupKey: 'kubechargeback', totalCostUnits: 2.3, cpuMcpu: 500, memMib: 512 },
          { groupKey: 'default', totalCostUnits: 0.5, cpuMcpu: 100, memMib: 128 }
        ]);
        setCompliance({
          summary: { ok: 15, missingRequests: 2, missingLimits: 3, bothMissing: 1 }
        });
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <div className="animate-spin bg-indigo-600 p-2 rounded-lg">
            <LayoutDashboard className="text-white w-8 h-8" />
          </div>
          <p className="text-slate-500 animate-pulse font-medium">Loading Dashboard Data...</p>
        </div>
      </div>
    )
  }

  const totalCost = allocations.reduce((sum, item) => sum + item.totalCostUnits, 0);
  
  const complianceTotal = compliance 
    ? (compliance.summary.ok + compliance.summary.missingRequests + compliance.summary.missingLimits + compliance.summary.bothMissing)
    : 0;
  
  const complianceScore = complianceTotal > 0 
    ? ((compliance.summary.ok / complianceTotal) * 100).toFixed(0) 
    : "100";

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Header */}
      <header className="bg-white border-b px-8 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="bg-indigo-600 p-2 rounded-lg shadow-lg shadow-indigo-100">
            <LayoutDashboard className="text-white w-6 h-6" />
          </div>
          <h1 className="text-xl font-bold text-slate-800 tracking-tight">KubeChargeback <span className="text-indigo-600">Dashboard</span></h1>
        </div>
        <div className="flex items-center gap-2 text-sm text-slate-500 bg-slate-100 px-3 py-1 rounded-full border border-slate-200">
          <Activity className="w-4 h-4 text-emerald-500" />
          Cluster Online
        </div>
      </header>

      <main className="p-8 max-w-7xl mx-auto space-y-8">
        {/* Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <StatCard title="Total Cost" value={totalCost.toFixed(2)} unit="Units" icon={<Wallet className="text-indigo-600" />} />
          <StatCard title="Compliance Score" value={complianceScore} unit="%" icon={<ShieldAlert className="text-amber-500" />} />
          <StatCard title="Workloads" value={complianceTotal.toString()} unit="Total" icon={<PieChartIcon className="text-emerald-500" />} />
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Allocation Chart */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
            <h3 className="text-lg font-semibold mb-6 text-slate-800">Cost by Namespace</h3>
            <div className="h-[300px] flex items-center justify-center">
              {allocations.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={allocations}
                      dataKey="totalCostUnits"
                      nameKey="groupKey"
                      cx="50%"
                      cy="50%"
                      innerRadius={60}
                      outerRadius={80}
                      paddingAngle={5}
                    >
                      {allocations.map((_entry, index) => (
                        <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip 
                      contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                    />
                    <Legend verticalAlign="bottom" height={36}/>
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div className="text-slate-400 flex flex-col items-center gap-2">
                  <PieChartIcon className="w-12 h-12 opacity-20" />
                  <p>No allocation data available</p>
                </div>
              )}
            </div>
          </div>

          {/* Compliance Summary */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
            <h3 className="text-lg font-semibold mb-6 text-slate-800">Compliance Summary</h3>
            <div className="space-y-4">
              <IssueRow label="Missing Requests" count={compliance?.summary.missingRequests || 0} color="bg-amber-50 text-amber-700 border-amber-100" />
              <IssueRow label="Missing Limits" count={compliance?.summary.missingLimits || 0} color="bg-orange-50 text-orange-700 border-orange-100" />
              <IssueRow label="Both Missing" count={compliance?.summary.bothMissing || 0} color="bg-red-50 text-red-700 border-red-100" />
              <div className="pt-6 border-t border-slate-100 mt-6 flex justify-between items-center">
                <div className="flex flex-col">
                  <span className="text-slate-500 font-medium text-sm">Healthy Workloads</span>
                  <span className="text-xs text-slate-400">Meeting all resource requirements</span>
                </div>
                <span className="text-emerald-600 text-2xl font-bold">{compliance?.summary.ok}</span>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}

function StatCard({ title, value, unit, icon }: { title: string, value: string, unit: string, icon: React.ReactNode }) {
  return (
    <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex items-start justify-between hover:shadow-md transition-all hover:-translate-y-1">
      <div>
        <p className="text-slate-500 text-sm font-semibold mb-1 uppercase tracking-wider">{title}</p>
        <div className="flex items-baseline gap-1">
          <span className="text-3xl font-extrabold text-slate-900">{value}</span>
          <span className="text-slate-400 text-sm font-medium">{unit}</span>
        </div>
      </div>
      <div className="bg-slate-50 p-3 rounded-xl border border-slate-100 shadow-inner">
        {icon}
      </div>
    </div>
  )
}

function IssueRow({ label, count, color }: { label: string, count: number, color: string }) {
  return (
    <div className="flex items-center justify-between p-4 rounded-xl border border-slate-100 bg-slate-50/30 hover:bg-slate-50 transition-colors">
      <span className="text-slate-700 text-sm font-semibold">{label}</span>
      <span className={`px-4 py-1.5 rounded-full text-xs font-black shadow-sm border ${color}`}>
        {count}
      </span>
    </div>
  )
}

export default App