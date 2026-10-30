package soka.simulasi;

import java.util.ArrayList;
import java.util.List;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.Vm;

/**
 * Factory VM siap pakai sesuai draft desain.
 * Tipe 1: 9 unit (ID 0-8), Tipe 2: 6 unit (ID 9-14).
 *
 * <p>Catatan desain: kedua tipe VM memakai 1 PE dan CloudletSchedulerSpaceShared, sehingga tiap VM
 * mengeksekusi satu cloudlet pada satu waktu. Ini sesuai model RasaScheduler (ECT = panjang / MIPS,
 * antrean serial per VM). Jika VM tipe 1 diberi 2 PE, VM itu bisa menjalankan 2 cloudlet paralel
 * dan estimasi completion time scheduler tidak lagi cocok dengan hasil simulasi.
 */
public final class VmFactory {

    private VmFactory() {}

    /**
     * @param brokerId user/broker ID pemilik VM
     * @return List&lt;Vm&gt; 15 unit siap pakai.
     */
    public static List<Vm> createVms(int brokerId) {
        List<Vm> vms = new ArrayList<>();
        // Tipe 1: ID 0-8
        for (int id = 0; id < 9; id++) {
            vms.add(new Vm(id, brokerId, 1000, 1, 2048, 1000, 10000,
                    "Xen", new CloudletSchedulerSpaceShared()));
        }
        // Tipe 2: ID 9-14
        for (int id = 9; id < 15; id++) {
            vms.add(new Vm(id, brokerId, 500, 1, 1024, 500, 5000,
                    "Xen", new CloudletSchedulerSpaceShared()));
        }
        return vms;
    }
}