package soka.simulasi;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

/** Smoke test: alokasi 15 VM + 15 cloudlet di datacenter 6 host. */
public class SimulasiTest {
    public static void main(String[] args) {
        try {
            CloudSim.init(1, Calendar.getInstance(), false);
            Datacenter dc = DatacenterCreator.createDatacenterShared("Datacenter_0");
            DatacenterBroker broker = new DatacenterBroker("Broker_0");
            int brokerId = broker.getId();

            List<Vm> vms = VmFactory.createVms(brokerId);
            broker.submitVmList(vms);

            List<Cloudlet> cloudlets = new LinkedList<>();
            for (int i = 0; i < 15; i++) {
                Cloudlet cl = new Cloudlet(i, 40000, 1, 300, 300,
                        new UtilizationModelFull(), new UtilizationModelFull(),
                        new UtilizationModelFull());
                cl.setUserId(brokerId);
                cloudlets.add(cl);
            }
            broker.submitCloudletList(cloudlets);

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            List<Cloudlet> hasil = broker.getCloudletReceivedList();
            Log.printLine("=== HASIL TEST ===");
            Log.printLine("Cloudlet sukses: " + hasil.size() + " / " + cloudlets.size());
            for (Cloudlet c : hasil) {
                Log.printLine(String.format("Cloudlet %d | VM %d | status %s | execTime %.2f",
                        c.getCloudletId(), c.getVmId(),
                        c.getStatus() == Cloudlet.SUCCESS ? "SUCCESS" : "GAGAL",
                        c.getActualCPUTime()));
            }
            Log.printLine(hasil.size() == cloudlets.size()
                    ? "TEST LULUS: datacenter bekerja."
                    : "TEST GAGAL: ada cloudlet tidak selesai.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
