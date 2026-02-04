import { useState, useEffect } from 'react'
import { LayoutDashboard, ShieldAlert, Wallet, PieChart as PieChartIcon, Activity, Bell, AlertTriangle } from 'lucide-react'
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend } from 'recharts'

interface Allocation {
  groupKey: string;
  totalCostUnits: number;
  cpuMcpu: number;
  memMib: number;
}

interface ComplianceItem {
  namespace: string;
  kind: string;
  name: string;
  complianceStatus: string;
}

interface Compliance {
  summary: {
    ok: number;
    missingRequests: number;
    missingLimits: number;
    bothMissing: number;
  };
  items: ComplianceItem[];
}

interface Alert {
  id: string;
  timestamp: string;
  severity: string;
  budgetName: string;
  message: string;
  details?: string;
}

interface AlertDetails {
  currentCpuMcpu: number;
  currentMemMib: number;
  limitCpuMcpu: number;
  limitMemMib: number;
  topOffenders: Array<{
    app: string;
    cpuMcpu: number;
    memMib: number;
    totalCostUnits: number;
  }>;
}

const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884d8'];

function App() {
  const [allocations, setAllocations] = useState<Allocation[]>([]);
  const [compliance, setCompliance] = useState<Compliance | null>(null);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedAlert, setExpandedAlert] = useState<string | null>(null);
  const [selectedComplianceFilter, setSelectedComplianceFilter] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const now = new Date();
        const start = new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString();
        const end = now.toISOString();

        const [allocRes, compRes, alertsRes] = await Promise.all([
          fetch(`/api/v1/reports/allocations?from=${start}&to=${end}&groupBy=namespace`),
          fetch(`/api/v1/reports/compliance?from=${start}&to=${end}`),
          fetch(`/api/v1/reports/alerts?limit=5`)
        ]);

        if (allocRes.ok) setAllocations(await allocRes.json());
        if (compRes.ok) setCompliance(await compRes.json());
        if (alertsRes.ok) setAlerts(await alertsRes.json());
      } catch (error) {
        console.error("Failed to fetch data, using mock data", error);
        setAllocations([
          { groupKey: 'kube-system', totalCostUnits: 12.5, cpuMcpu: 2000, memMib: 4096 },
          { groupKey: 'kubechargeback', totalCostUnits: 2.3, cpuMcpu: 500, memMib: 512 },
          { groupKey: 'default', totalCostUnits: 0.5, cpuMcpu: 100, memMib: 128 }
        ]);
        setCompliance({
          summary: { ok: 15, missingRequests: 2, missingLimits: 3, bothMissing: 1 },
          items: [
            { namespace: 'default', kind: 'Deployment', name: 'app-1', complianceStatus: 'MISSING_LIMITS' },
            { namespace: 'default', kind: 'Deployment', name: 'app-2', complianceStatus: 'BOTH_MISSING' },
            { namespace: 'kube-system', kind: 'DaemonSet', name: 'proxy', complianceStatus: 'MISSING_REQUESTS' },
            { namespace: 'kube-system', kind: 'Deployment', name: 'coredns', complianceStatus: 'OK' },
          ]
        });
        setAlerts([
          { id: '1', timestamp: new Date().toISOString(), severity: 'CRITICAL', budgetName: 'marketing-budget', message: "Budget 'marketing-budget' exceeded. Severity: CRITICAL" }
        ]);
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
  
  const complianceScore = complianceTotal > 0 && compliance
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
        <div className="flex items-center gap-4">
          <a href="#alerts" className="relative block group">
            <Bell className="w-6 h-6 text-slate-400 cursor-pointer group-hover:text-indigo-600 transition-colors" />
            {alerts.length > 0 && (
              <span className="absolute -top-1 -right-1 bg-red-500 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full border-2 border-white">
                {alerts.length}
              </span>
            )}
          </a>
          <div className="flex items-center gap-2 text-sm text-slate-500 bg-slate-100 px-3 py-1 rounded-full border border-slate-200">
            <Activity className="w-4 h-4 text-emerald-500" />
            Cluster Online
          </div>
        </div>
      </header>

      <main className="p-8 max-w-7xl mx-auto space-y-8">
        {/* Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <StatCard title="Total Cost" value={totalCost.toFixed(2)} unit="Units" icon={<Wallet className="text-indigo-600" />} href="#cost-chart" />
          <StatCard title="Compliance Score" value={complianceScore} unit="%" icon={<ShieldAlert className="text-amber-500" />} href="#compliance-summary" />
          <StatCard title="Workloads" value={complianceTotal.toString()} unit="Total" icon={<PieChartIcon className="text-emerald-500" />} href="#compliance-summary" />
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Allocation Chart */}
          <div id="cost-chart" className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm hover:shadow-md transition-shadow scroll-mt-24">
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
          <div id="compliance-summary" className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm hover:shadow-md transition-shadow scroll-mt-24">
            <h3 className="text-lg font-semibold mb-6 text-slate-800">Compliance Summary</h3>
            <div className="space-y-4">
              <IssueRow 
                label="Missing Requests" 
                count={(compliance?.summary.missingRequests || 0) + (compliance?.summary.bothMissing || 0)} 
                color="bg-amber-50 text-amber-700 border-amber-100" 
                onClick={() => setSelectedComplianceFilter(selectedComplianceFilter === 'MISSING_REQUESTS' ? null : 'MISSING_REQUESTS')}
                isActive={selectedComplianceFilter === 'MISSING_REQUESTS'}
              />
              <IssueRow 
                label="Missing Limits" 
                count={(compliance?.summary.missingLimits || 0) + (compliance?.summary.bothMissing || 0)} 
                color="bg-orange-50 text-orange-700 border-orange-100" 
                onClick={() => setSelectedComplianceFilter(selectedComplianceFilter === 'MISSING_LIMITS' ? null : 'MISSING_LIMITS')}
                isActive={selectedComplianceFilter === 'MISSING_LIMITS'}
              />
              <IssueRow 
                label="Both Missing" 
                count={compliance?.summary.bothMissing || 0} 
                color="bg-red-50 text-red-700 border-red-100" 
                onClick={() => setSelectedComplianceFilter(selectedComplianceFilter === 'BOTH_MISSING' ? null : 'BOTH_MISSING')}
                isActive={selectedComplianceFilter === 'BOTH_MISSING'}
              />
              <div 
                onClick={() => setSelectedComplianceFilter(selectedComplianceFilter === 'OK' ? null : 'OK')}
                className={`pt-6 border-t border-slate-100 mt-6 flex justify-between items-center cursor-pointer p-2 rounded-lg transition-colors ${
                  selectedComplianceFilter === 'OK' ? 'bg-emerald-50 border-t-emerald-200' : 'hover:bg-slate-50'
                }`}
              >
                <div className="flex flex-col">
                  <span className={`font-medium text-sm ${selectedComplianceFilter === 'OK' ? 'text-emerald-900' : 'text-slate-500'}`}>Healthy Workloads</span>
                  <span className="text-xs text-slate-400">Meeting all resource requirements</span>
                </div>
                <span className={`text-2xl font-bold ${selectedComplianceFilter === 'OK' ? 'text-emerald-700' : 'text-emerald-600'}`}>{compliance?.summary.ok}</span>
              </div>
            </div>

            {/* Compliance Details List */}
            {selectedComplianceFilter && (
              <div className="mt-8 pt-6 border-t border-slate-100 animate-in fade-in slide-in-from-top-4 duration-300">
                <div className="flex items-center justify-between mb-4">
                  <h4 className="font-bold text-slate-800 flex items-center gap-2">
                    <Activity className="w-4 h-4 text-indigo-600" />
                    Details: {selectedComplianceFilter.replace('_', ' ')}
                  </h4>
                  <button 
                    onClick={() => setSelectedComplianceFilter(null)}
                    className="text-xs font-bold text-indigo-600 hover:text-indigo-800 uppercase tracking-tight"
                  >
                    Clear Filter
                  </button>
                </div>
                <div className="max-h-[300px] overflow-y-auto space-y-2 pr-2 custom-scrollbar">
                  {compliance?.items
                    .filter(item => {
                      if (selectedComplianceFilter === 'MISSING_REQUESTS') {
                        return item.complianceStatus === 'MISSING_REQUESTS' || item.complianceStatus === 'BOTH_MISSING';
                      }
                      if (selectedComplianceFilter === 'MISSING_LIMITS') {
                        return item.complianceStatus === 'MISSING_LIMITS' || item.complianceStatus === 'BOTH_MISSING';
                      }
                      return item.complianceStatus === selectedComplianceFilter;
                    })
                    .map((item, idx) => (
                      <div key={idx} className="flex items-center justify-between bg-slate-50 p-3 rounded-xl border border-slate-100 group hover:border-indigo-200 transition-colors">
                        <div className="flex flex-col">
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-bold text-indigo-600 uppercase tracking-tighter">{item.kind}</span>
                            {item.complianceStatus === 'BOTH_MISSING' && selectedComplianceFilter !== 'BOTH_MISSING' && (
                              <span className="text-[8px] font-black bg-red-100 text-red-600 px-1 rounded">ALSO MISSING {selectedComplianceFilter === 'MISSING_REQUESTS' ? 'LIMITS' : 'REQUESTS'}</span>
                            )}
                          </div>
                          <span className="text-sm font-bold text-slate-700">{item.name}</span>
                        </div>
                        <span className="text-[10px] font-bold bg-white px-2 py-1 rounded border border-slate-200 text-slate-500">{item.namespace}</span>
                      </div>
                    ))}
                  {compliance?.items.filter(item => {
                    if (selectedComplianceFilter === 'MISSING_REQUESTS') {
                      return item.complianceStatus === 'MISSING_REQUESTS' || item.complianceStatus === 'BOTH_MISSING';
                    }
                    if (selectedComplianceFilter === 'MISSING_LIMITS') {
                      return item.complianceStatus === 'MISSING_LIMITS' || item.complianceStatus === 'BOTH_MISSING';
                    }
                    return item.complianceStatus === selectedComplianceFilter;
                  }).length === 0 && (
                    <p className="text-sm text-slate-400 text-center py-4 italic">No workloads found with this status.</p>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Recent Alerts */}
        {alerts.length > 0 && (
          <div id="alerts" className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm scroll-mt-24">
            <h3 className="text-lg font-semibold mb-6 text-slate-800 flex items-center gap-2">
              <Bell className="w-5 h-5 text-indigo-600" />
              Recent Budget Notifications
            </h3>
            <div className="space-y-3">
              {alerts.map(alert => {
                const isExpanded = expandedAlert === alert.id;
                let details: AlertDetails | null = null;
                if (alert.details) {
                  try {
                    details = JSON.parse(alert.details);
                  } catch (e) {
                    console.error("Failed to parse alert details", e);
                  }
                }

                return (
                  <div 
                    key={alert.id} 
                    className={`flex flex-col p-4 rounded-xl border transition-all cursor-pointer ${
                      isExpanded ? 'border-indigo-200 bg-indigo-50/30' : 'border-slate-100 bg-slate-50/50 hover:bg-slate-50'
                    }`}
                    onClick={() => setExpandedAlert(isExpanded ? null : alert.id)}
                  >
                    <div className="flex items-start gap-4">
                      <div className={`p-2 rounded-lg ${alert.severity === 'CRITICAL' ? 'bg-red-100 text-red-600' : 'bg-amber-100 text-amber-600'}`}>
                        <AlertTriangle className="w-5 h-5" />
                      </div>
                      <div className="flex-1">
                        <div className="flex items-center justify-between mb-1">
                          <span className="font-bold text-slate-900">{alert.budgetName}</span>
                          <span className="text-xs text-slate-400 font-medium">{new Date(alert.timestamp).toLocaleString()}</span>
                        </div>
                        <p className="text-sm text-slate-600">{alert.message}</p>
                      </div>
                      <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${alert.severity === 'CRITICAL' ? 'bg-red-100 text-red-700' : 'bg-amber-100 text-amber-700'}`}>
                        {alert.severity}
                      </span>
                    </div>

                    {isExpanded && details && (
                      <div className="mt-4 pt-4 border-t border-indigo-100 animate-in fade-in slide-in-from-top-2 duration-200">
                        <div className="grid grid-cols-2 gap-4 mb-4">
                          <div className="bg-white/60 p-3 rounded-lg border border-indigo-50">
                            <p className="text-[10px] text-slate-400 font-bold uppercase mb-1">CPU Usage</p>
                            <p className="text-sm font-bold text-slate-700">{details.currentCpuMcpu}m / {details.limitCpuMcpu}m</p>
                          </div>
                          <div className="bg-white/60 p-3 rounded-lg border border-indigo-50">
                            <p className="text-[10px] text-slate-400 font-bold uppercase mb-1">Memory Usage</p>
                            <p className="text-sm font-bold text-slate-700">{details.currentMemMib} MiB / {details.limitMemMib} MiB</p>
                          </div>
                        </div>
                        
                        <p className="text-xs font-bold text-slate-500 uppercase mb-2 px-1">Top Offenders</p>
                        <div className="space-y-1">
                          {details.topOffenders.map((off, idx) => (
                            <div key={idx} className="flex items-center justify-between bg-white/40 p-2 rounded-lg text-xs">
                              <span className="font-semibold text-slate-700">{off.app}</span>
                              <div className="flex items-center gap-4 text-slate-500">
                                <span>{off.cpuMcpu}m CPU</span>
                                <span>{off.memMib} MiB</span>
                                <span className="font-bold text-indigo-600">{off.totalCostUnits.toFixed(2)} Units</span>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </main>
    </div>
  )
}

function StatCard({ title, value, unit, icon, href }: { title: string, value: string, unit: string, icon: React.ReactNode, href?: string }) {
  const content = (
    <>
      <div>
        <p className="text-slate-500 text-sm font-semibold mb-1 uppercase tracking-wider">{title}</p>
        <div className="flex items-baseline gap-1">
          <span className="text-3xl font-extrabold text-slate-900">{value}</span>
          <span className="text-slate-400 text-sm font-medium">{unit}</span>
        </div>
      </div>
      <div className="bg-slate-50 p-3 rounded-xl border border-slate-100 shadow-inner group-hover:bg-indigo-50 transition-colors">
        {icon}
      </div>
    </>
  );

  if (href) {
    return (
      <a href={href} className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex items-start justify-between hover:shadow-md transition-all hover:-translate-y-1 group">
        {content}
      </a>
    );
  }

  return (
    <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex items-start justify-between">
      {content}
    </div>
  )
}

function IssueRow({ label, count, color, onClick, isActive }: { label: string, count: number, color: string, onClick?: () => void, isActive?: boolean }) {
  return (
    <div 
      onClick={onClick}
      className={`flex items-center justify-between p-4 rounded-xl border transition-all cursor-pointer ${
        isActive 
          ? 'border-indigo-300 bg-indigo-50 shadow-sm ring-1 ring-indigo-200' 
          : 'border-slate-100 bg-slate-50/30 hover:bg-slate-50 hover:border-slate-200'
      }`}
    >
      <span className={`text-sm font-semibold ${isActive ? 'text-indigo-900' : 'text-slate-700'}`}>{label}</span>
      <span className={`px-4 py-1.5 rounded-full text-xs font-black shadow-sm border ${color}`}>
        {count}
      </span>
    </div>
  )
}

export default App