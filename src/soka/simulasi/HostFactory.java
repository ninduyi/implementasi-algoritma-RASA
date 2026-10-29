package soka.simulasi;

import java.util.ArrayList;
import java.util.List;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

/**
 * Factory Host siap pakai sesuai draft desain.
 * Tipe A: 3 unit (ID 0-2), Tipe B: 3 unit (ID 3-5).
 */
public final class HostFactory {

    private HostFactory() {}

    /** @return List&lt;Host&gt; 6 unit siap pakai. */
    public static List<Host> createHosts() {
        List<Host> hosts = new ArrayList<>();
        // Tipe A: ID 0-2
        for (int id = 0; id < 3; id++) {
            hosts.add(createHost(id, 4000, 8, 16384, 10000, 1000000));
        }
        // Tipe B: ID 3-5
        for (int id = 3; id < 6; id++) {
            hosts.add(createHost(id, 2000, 4, 8192, 5000, 1000000));
        }
        return hosts;
    }

    public static Host createHost(int id, int mipsPerPe, int peCount,
                                  int ramMb, long bwMbps, long storageMb) {
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < peCount; i++) {
            peList.add(new Pe(i, new PeProvisionerSimple(mipsPerPe)));
        }
        return new Host(
                id,
                new RamProvisionerSimple(ramMb),
                new BwProvisionerSimple(bwMbps),
                storageMb,
                peList,
                new VmSchedulerTimeShared(peList));
    }
}