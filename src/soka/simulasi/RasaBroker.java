package soka.simulasi;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

// Jembatan antara RasaScheduler (logic murni) dan CloudSim (Datacenter/Host/VM).
public class RasaBroker extends DatacenterBroker {

    private List<RasaScheduler.Assignment> lastAssignments;

    public RasaBroker(String name) throws Exception {
        super(name);
    }

    public List<RasaScheduler.Assignment> scheduleWithRasa(List<Vm> vmsInOrder) {
        return scheduleWith(RasaScheduler.Mode.RASA, vmsInOrder);
    }

    // Jadwalkan dengan RASA / Min-Min / Max-Min, ikat cloudlet ke VM,
    // lalu urutkan ulang antrean broker sesuai urutan keputusan algoritma
    // (perlu untuk CloudletSchedulerSpaceShared, yang mengantre per urutan submit).
    public List<RasaScheduler.Assignment> scheduleWith(RasaScheduler.Mode mode, List<Vm> vmsInOrder) {
        List<Cloudlet> antrian = getCloudletList();
        int nTask = antrian.size();
        int nVm = vmsInOrder.size();

        double[] taskLengthsMI = new double[nTask];
        for (int i = 0; i < nTask; i++) {
            taskLengthsMI[i] = antrian.get(i).getCloudletLength();
        }
        double[] vmMips = new double[nVm];
        for (int j = 0; j < nVm; j++) {
            vmMips[j] = vmsInOrder.get(j).getMips();
        }

        List<RasaScheduler.Assignment> hasil = RasaScheduler.schedule(taskLengthsMI, vmMips, mode);

        List<Cloudlet> urut = new ArrayList<>();
        for (RasaScheduler.Assignment a : hasil) {
            Cloudlet c = antrian.get(a.taskIndex);
            int vmId = vmsInOrder.get(a.vmIndex).getId();
            bindCloudletToVm(c.getCloudletId(), vmId);
            urut.add(c);
        }
        antrian.clear();
        antrian.addAll(urut);

        Log.printLine(getName() + ": " + mode + " selesai menjadwalkan " + hasil.size()
                + " cloudlet ke " + nVm + " VM.");

        this.lastAssignments = hasil;
        return hasil;
    }

    public List<RasaScheduler.Assignment> getLastAssignments() {
        return lastAssignments;
    }
}