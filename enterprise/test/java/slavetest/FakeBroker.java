package slavetest;

import java.util.HashMap;
import java.util.Map;

import org.neo4j.kernel.Config;
import org.neo4j.kernel.HighlyAvailableGraphDatabase;
import org.neo4j.kernel.impl.ha.Master;
import org.neo4j.kernel.impl.ha.SlaveContext;
import org.neo4j.kernel.impl.transaction.xaframework.XaDataSource;

public class FakeBroker extends AbstractBroker
{
    private final Master master;
    private final int slaveNumber;

    FakeBroker( Master master, int slaveNumber )
    {
        this.master = master;
        this.slaveNumber = slaveNumber;
    }
    
    public Master getMaster()
    {
        return master;
    }

    public SlaveContext getSlaveContext()
    {
        Config config = ((HighlyAvailableGraphDatabase) getDb()).getConfig();
        Map<String, Long> txs = new HashMap<String, Long>();
        for ( XaDataSource dataSource :
                config.getTxModule().getXaDataSourceManager().getAllRegisteredDataSources() )
        {
            txs.put( dataSource.getName(), dataSource.getLastCommittedTxId() );
        }
        return new SlaveContext( slaveNumber, txs );
    }
}
