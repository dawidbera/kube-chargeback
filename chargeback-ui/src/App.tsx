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
  const [selectedNamespace, setSelectedNamespace] = useState<string | null>(null);
  const [topApps, setTopApps] = useState<Allocation[]>([]);
  const [showTopApps, setShowTopApps] = useState(false);
  const [showAllWorkloads, setShowAllWorkloads] = useState(false);
  const [showNamespaceList, setShowNamespaceList] = useState(false);
  const [showCostTable, setShowCostTable] = useState(false);
  const [bellRinging, setBellRinging] = useState(false);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const now = new Date();
        const start = new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString();
        const end = now.toISOString();

        const [allocRes, compRes, alertsRes, topAppsRes] = await Promise.all([
          fetch(`/api/v1/reports/allocations?from=${start}&to=${end}&groupBy=namespace`),
          fetch(`/api/v1/reports/compliance?from=${start}&to=${end}`),
          fetch(`/api/v1/reports/alerts?limit=5`),
          fetch(`/api/v1/reports/top-apps?from=${start}&to=${end}&limit=10`)
        ]);

        if (allocRes.ok) setAllocations(await allocRes.json());
        if (compRes.ok) setCompliance(await compRes.json());
        if (alertsRes.ok) setAlerts(await alertsRes.json());
        if (topAppsRes.ok) setTopApps(await topAppsRes.json());
      } catch (error) {
        console.error("Failed to fetch data, using mock data", error);
        setAllocations([
          { groupKey: 'kube-system', totalCostUnits: 12.5, cpuMcpu: 2000, memMib: 4096 },
          { groupKey: 'kubechargeback', totalCostUnits: 2.3, cpuMcpu: 500, memMib: 512 },
          { groupKey: 'dev', totalCostUnits: 19.85, cpuMcpu: 3000, memMib: 8192 }
        ]);
        setTopApps([
          { groupKey: 'nginx-ingress', totalCostUnits: 8.5, cpuMcpu: 1000, memMib: 2048 },
          { groupKey: 'redis-master', totalCostUnits: 4.2, cpuMcpu: 500, memMib: 1024 },
          { groupKey: 'api-gateway', totalCostUnits: 3.8, cpuMcpu: 400, memMib: 512 }
        ]);
        setCompliance({
          summary: { ok: 8, missingRequests: 0, missingLimits: 4, bothMissing: 5 },
          items: [
            { namespace: 'dev', kind: 'Deployment', name: 'payments', complianceStatus: 'MISSING_LIMITS' },
            { namespace: 'dev', kind: 'Deployment', name: 'auth', complianceStatus: 'BOTH_MISSING' },
            { namespace: 'kube-system', kind: 'DaemonSet', name: 'aws-node', complianceStatus: 'OK' },
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
          <div className="relative group">
            <button 
              onClick={() => {
                setBellRinging(true);
                setTimeout(() => setBellRinging(false), 1000);
                const el = document.getElementById('alerts');
                if (el) el.scrollIntoView({ behavior: 'smooth' });
              }}
              className={`p-2 rounded-full transition-all ${bellRinging ? 'bg-indigo-100 scale-110' : 'hover:bg-slate-100'}`}
            >
              <Bell className={`w-6 h-6 transition-colors ${bellRinging ? 'text-indigo-600 fill-indigo-200' : 'text-slate-400 group-hover:text-indigo-600'}`} />
              {alerts.length > 0 && (
                <span className="absolute top-1 right-1 bg-red-500 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full border-2 border-white">
                  {alerts.length}
                </span>
              )}
            </button>
          </div>
          <div 
            onClick={() => setShowNamespaceList(!showNamespaceList)}
            className={`flex items-center gap-2 text-sm cursor-pointer transition-colors px-3 py-1 rounded-full border ${
              showNamespaceList ? 'bg-indigo-600 text-white border-indigo-600' : 'text-slate-500 bg-slate-100 border-slate-200 hover:bg-slate-200'
            }`}
          >
            <Activity className={`w-4 h-4 ${showNamespaceList ? 'text-white' : 'text-emerald-500'}`} />
            Cluster Online
          </div>
        </div>
      </header>

      {showNamespaceList && (
        <div className="bg-indigo-600 text-white px-8 py-3 animate-in fade-in slide-in-from-top-2 duration-300 border-t border-indigo-500 shadow-inner">
          <div className="max-w-7xl mx-auto flex items-center gap-4 text-xs font-bold uppercase tracking-widest">
            <span>Monitored Namespaces:</span>
            <div className="flex gap-2 flex-wrap">
              {allocations.map(a => (
                <span key={a.groupKey} className="bg-white/20 px-2 py-0.5 rounded">{a.groupKey}</span>
              ))}
            </div>
          </div>
        </div>
      )}

      <main className="p-8 max-w-7xl mx-auto space-y-8">
        {/* Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <StatCard 
            title="Total Cost" 
            value={totalCost.toFixed(2)} 
            unit="Units" 
            icon={<Wallet className={showTopApps ? "text-white" : "text-indigo-600"} />} 
            onClick={() => {
              setShowTopApps(!showTopApps);
              setShowAllWorkloads(false);
              setSelectedComplianceFilter(null);
            }}
            isActive={showTopApps}
          />
          <StatCard 
            title="Compliance Score" 
            value={complianceScore} 
            unit="%" 
            icon={<ShieldAlert className={showAllWorkloads && !selectedComplianceFilter ? "text-white" : "text-amber-500"} />} 
            onClick={() => {
              setShowAllWorkloads(!showAllWorkloads);
              setShowTopApps(false);
              setSelectedComplianceFilter(null);
            }}
            isActive={showAllWorkloads && !selectedComplianceFilter}
          />
          <StatCard 
            title="Workloads" 
            value={complianceTotal.toString()} 
            unit="Total" 
            icon={<PieChartIcon className={showAllWorkloads && !selectedComplianceFilter ? "text-white" : "text-emerald-500"} />} 
            onClick={() => {
              setShowAllWorkloads(!showAllWorkloads);
              setShowTopApps(false);
              setSelectedComplianceFilter(null);
              setSelectedNamespace(null);
            }}
            isActive={showAllWorkloads && !selectedComplianceFilter}
          />
        </div>

        {showTopApps && (
          <div className="bg-white p-6 rounded-2xl border-2 border-indigo-100 shadow-xl animate-in zoom-in-95 duration-200">
            <div className="flex items-center justify-between mb-6">
              <h3 className="text-lg font-bold text-slate-800 flex items-center gap-2">
                <Wallet className="w-5 h-5 text-indigo-600" />
                Cost Breakdown (Top 10 Apps)
              </h3>
              <button onClick={() => setShowTopApps(false)} className="text-xs font-black text-slate-400 hover:text-slate-600 uppercase tracking-tighter">Close</button>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {topApps.map((app, idx) => (
                <div key={idx} className="flex items-center justify-between p-4 bg-slate-50 rounded-xl border border-slate-100 hover:border-indigo-200 transition-colors">
                  <span className="font-bold text-slate-700">{app.groupKey}</span>
                  <div className="flex items-center gap-4">
                    <span className="text-xs text-slate-400 font-medium">{app.cpuMcpu}m CPU / {app.memMib}MiB</span>
                    <span className="text-indigo-600 font-black">{app.totalCostUnits.toFixed(2)} Units</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Allocation Chart */}
          <div 
            id="cost-chart" 
            className={`bg-white p-6 rounded-2xl border transition-all scroll-mt-24 cursor-pointer group/card ${
              selectedNamespace ? 'border-indigo-300 ring-2 ring-indigo-50 shadow-lg' : 'border-slate-200 shadow-sm hover:border-indigo-200'
            }`}
            onClick={(e) => {
              // Only reset if clicking the card background, not the pie itself (which has its own handler)
              if (e.target === e.currentTarget) {
                setSelectedNamespace(null);
              }
            }}
          >
            <div className="flex items-center justify-between mb-6">
              <h3 className="text-lg font-semibold text-slate-800 flex items-center gap-2">
                Cost by Namespace
                {selectedNamespace && <span className="text-xs bg-indigo-600 text-white px-2 py-0.5 rounded-full animate-pulse">Filtered</span>}
              </h3>
              <div className="flex gap-2">
                <button 
                  onClick={(e) => {
                    e.stopPropagation();
                    setShowCostTable(!showCostTable);
                  }}
                  className={`text-[10px] font-black px-2 py-1 rounded transition-colors uppercase ${
                    showCostTable ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                  }`}
                >
                  {showCostTable ? 'Show Chart' : 'Show Table'}
                </button>
                {selectedNamespace && (
                  <button 
                    onClick={(e) => {
                      e.stopPropagation();
                      setSelectedNamespace(null);
                    }}
                    className="text-[10px] font-black bg-indigo-100 text-indigo-700 px-2 py-1 rounded hover:bg-indigo-200 uppercase"
                  >
                    Reset Filter
                  </button>
                )}
              </div>
            </div>
            <div className="h-[300px] flex items-center justify-center">
              {showCostTable ? (
                <div className="w-full h-full overflow-y-auto custom-scrollbar">
                  <table className="w-full text-left text-xs">
                    <thead className="sticky top-0 bg-white">
                      <tr className="border-b border-slate-100">
                        <th className="py-2 font-bold text-slate-400 uppercase tracking-tighter">Namespace</th>
                        <th className="py-2 font-bold text-slate-400 uppercase tracking-tighter text-right">CPU</th>
                        <th className="py-2 font-bold text-slate-400 uppercase tracking-tighter text-right">Cost</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-50">
                      {allocations.map((a, idx) => (
                        <tr 
                          key={idx} 
                          className={`hover:bg-slate-50 cursor-pointer ${selectedNamespace === a.groupKey ? 'bg-indigo-50' : ''}`}
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedNamespace(a.groupKey === selectedNamespace ? null : a.groupKey);
                          }}
                        >
                          <td className="py-3 font-bold text-slate-700">{a.groupKey}</td>
                          <td className="py-3 text-right text-slate-500">{a.cpuMcpu}m</td>
                          <td className="py-3 text-right font-black text-indigo-600">{a.totalCostUnits.toFixed(2)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : allocations.length > 0 ? (
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
                      onClick={(data) => {
                        setSelectedNamespace(data.groupKey === selectedNamespace ? null : data.groupKey);
                        setShowAllWorkloads(false);
                      }}
                      cursor="pointer"
                    >
                      {allocations.map((_entry, index) => (
                        <Cell 
                          key={`cell-${index}`} 
                          fill={COLORS[index % COLORS.length]} 
                          stroke={_entry.groupKey === selectedNamespace ? '#4f46e5' : 'none'}
                          strokeWidth={3}
                          className="hover:opacity-80 transition-opacity"
                        />
                      ))}
                    </Pie>
                    <Tooltip 
                      contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                    />
                    <Legend 
                      verticalAlign="bottom" 
                      height={36} 
                      onClick={(data) => {
                        const val = data.value as string;
                        setSelectedNamespace(val === selectedNamespace ? null : val);
                      }}
                      wrapperStyle={{ cursor: 'pointer' }}
                    />
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
          <div id="compliance-summary" className={`bg-white p-6 rounded-2xl border transition-all scroll-mt-24 ${selectedComplianceFilter || showAllWorkloads || selectedNamespace ? 'border-indigo-200 shadow-lg' : 'border-slate-200 shadow-sm'}`}>
            <h3 className="text-lg font-semibold mb-6 text-slate-800">
              {showAllWorkloads ? 'Workload Inventory' : selectedNamespace ? `Workloads in ${selectedNamespace}` : 'Compliance Summary'}
            </h3>
            
            {!showAllWorkloads && !selectedNamespace && (
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
            )}

            {/* General Workload List */}
            {(selectedComplianceFilter || showAllWorkloads || selectedNamespace) && (
              <div className="animate-in fade-in slide-in-from-top-4 duration-300">
                <div className="flex items-center justify-between mb-4">
                  <h4 className="font-bold text-slate-800 flex items-center gap-2 text-sm uppercase tracking-tight">
                    <Activity className="w-4 h-4 text-indigo-600" />
                    {selectedComplianceFilter ? `Status: ${selectedComplianceFilter.replace('_', ' ')}` : showAllWorkloads ? 'All Workloads' : `Namespace: ${selectedNamespace}`}
                  </h4>
                  <button 
                    onClick={() => {
                      setSelectedComplianceFilter(null);
                      setShowAllWorkloads(false);
                      setSelectedNamespace(null);
                    }}
                    className="text-[10px] font-black text-indigo-600 hover:text-indigo-800 uppercase tracking-tight border-b-2 border-indigo-100"
                  >
                    Clear Filter
                  </button>
                </div>
                <div className="max-h-[400px] overflow-y-auto space-y-2 pr-2 custom-scrollbar">
                  {compliance?.items
                    .filter(item => {
                      if (showAllWorkloads) return true;
                      if (selectedNamespace) return item.namespace === selectedNamespace;
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
                            {item.complianceStatus !== 'OK' && (
                              <span className={`text-[8px] font-black px-1 rounded ${
                                item.complianceStatus === 'BOTH_MISSING' ? 'bg-red-100 text-red-600' : 'bg-amber-100 text-amber-600'
                              }`}>
                                {item.complianceStatus.replace('_', ' ')}
                              </span>
                            )}
                          </div>
                          <span className="text-sm font-bold text-slate-700">{item.name}</span>
                        </div>
                        <span className="text-[10px] font-bold bg-white px-2 py-1 rounded border border-slate-200 text-slate-500">{item.namespace}</span>
                      </div>
                    ))}
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

function StatCard({ title, value, unit, icon, href, onClick, isActive }: { title: string, value: string, unit: string, icon: React.ReactNode, href?: string, onClick?: () => void, isActive?: boolean }) {
  const content = (
    <>
      <div className="relative z-10 text-left">
        <p className={`text-[10px] font-bold mb-1 uppercase tracking-widest ${isActive ? 'text-indigo-100' : 'text-slate-500'}`}>{title}</p>
        <div className="flex items-baseline gap-1">
          <span className={`text-3xl font-black ${isActive ? 'text-white' : 'text-slate-900'}`}>{value}</span>
          <span className={`text-sm font-bold ${isActive ? 'text-indigo-200' : 'text-slate-400'}`}>{unit}</span>
        </div>
      </div>
      <div className={`p-3 rounded-xl border transition-colors relative z-10 ${
        isActive 
          ? 'bg-white/20 border-white/30 text-white' 
          : 'bg-slate-50 border-slate-100 shadow-inner group-hover:bg-indigo-50'
      }`}>
        {icon}
      </div>
      {isActive && (
        <div className="absolute inset-0 bg-indigo-600 rounded-2xl animate-in fade-in duration-300"></div>
      )}
    </>
  );

  const className = `w-full bg-white p-6 rounded-2xl border transition-all hover:-translate-y-1 group relative overflow-hidden flex items-start justify-between ${
    isActive 
      ? 'border-indigo-600 shadow-lg shadow-indigo-100' 
      : 'border-slate-200 shadow-sm hover:shadow-md'
  }`;

  if (onClick) {
    return (
      <button onClick={onClick} className={className}>
        {content}
      </button>
    );
  }

  if (href) {
    return (
      <a href={href} className={className}>
        {content}
      </a>
    );
  }

  return (
    <div className={className}>
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