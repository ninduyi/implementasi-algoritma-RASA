package soka.simulasi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;

/**
 * Penempatan VM deterministik sesuai Gambar 1 draft desain:
 * VM 0-8 (Tipe 1) -> Host A (0-2), 3 VM per host;
 * VM 9-14 (Tipe 2) -> Host B (3-5), 2 VM per host.
 * (VmAllocationPolicySimple bawaan menaruh SEMUA VM di Host A karena memilih host dengan PE bebas terbanyak.)
 */
public class VmAllocationPolicyByType extends VmAllocationPolicy {
    private final Map<String, Host> vmTable = new HashMap<>();

    public VmAllocationPolicyByType(List<? extends Host> list) { super(list); }

    private int targetHost(int vmId) {
        return vmId < 9 ? vmId / 3 : 3 + (vmId - 9) / 2;
    }

    @Override public boolean allocateHostForVm(Vm vm) {
        List<Host> hosts = getHostList();
        int idx = targetHost(vm.getId());
        return idx < hosts.size() && allocateHostForVm(vm, hosts.get(idx));
    }

    @Override public boolean allocateHostForVm(Vm vm, Host host) {
        if (host.vmCreate(vm)) { vmTable.put(vm.getUid(), host); return true; }
        return false;
    }

    @Override public void deallocateHostForVm(Vm vm) {
        Host h = vmTable.remove(vm.getUid());
        if (h != null) h.vmDestroy(vm);
    }

    @Override public Host getHost(Vm vm) { return vmTable.get(vm.getUid()); }
    @Override public Host getHost(int vmId, int userId) { return vmTable.get(Vm.getUid(userId, vmId)); }
    @Override public List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList) { return null; }
}