package soka.simulasi;

import java.util.LinkedList;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;

/**
 * Factory Datacenter siap pakai (VmAllocationPolicySimple).
 */
public final class DatacenterCreator {

    private DatacenterCreator() {}

    public static Datacenter createDatacenter(String name) throws Exception {
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                "x86", "Linux", "Xen",
                HostFactory.createHosts(),
                10.0,   // time zone
                3.0,    // cost per second
                0.05,   // cost per mem
                0.001,  // cost per storage
                0.1);   // cost per bw

        return new Datacenter(name, characteristics,
                new VmAllocationPolicySimple(HostFactory.createHosts()),
                new LinkedList<Storage>(), 0);
    }

    /**
     * Versi hemat: pakai SATU list host yang sama untuk
     * characteristics & allocation policy (hindari duplikasi objek).
     */
    public static Datacenter createDatacenterShared(String name) throws Exception {
        java.util.List<org.cloudbus.cloudsim.Host> hosts = HostFactory.createHosts();
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                "x86", "Linux", "Xen", hosts,
                10.0, 3.0, 0.05, 0.001, 0.1);
        return new Datacenter(name, characteristics,
                new VmAllocationPolicyByType(hosts),
                new LinkedList<Storage>(), 0);
    }
}