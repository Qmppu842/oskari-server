package fi.nls.oskari.service.capabilities;

import java.sql.Timestamp;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import fi.nls.oskari.annotation.Oskari;
import fi.nls.oskari.db.DatasourceHelper;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.mybatis.MyBatisHelper;

@Oskari
public class CapabilitiesCacheServiceMybatisImpl extends CapabilitiesCacheService {

    private static final Logger LOG = LogFactory.getLogger(CapabilitiesCacheServiceMybatisImpl.class);

    private final SqlSessionFactory factory;

    public CapabilitiesCacheServiceMybatisImpl() {
        this(DatasourceHelper.getInstance().getDataSource());
    }

    public CapabilitiesCacheServiceMybatisImpl(DataSource ds) {
        this.factory = MyBatisHelper.initMyBatis(ds, CapabilitiesMapper.class);
    }

    private CapabilitiesMapper getMapper(SqlSession session) {
        return session.getMapper(CapabilitiesMapper.class);
    }

    /**
     * Tries to load capabilities from the database
     * @return null if not saved to db
     * @throws IllegalArgumentException if url or layertype is null or empty
     */
    public OskariLayerCapabilities find(String url, String layertype, String version)
            throws IllegalArgumentException {
        url = OskariLayerCapabilitiesDraft.trim(url, "url");
        layertype = OskariLayerCapabilitiesDraft.trim(layertype, "layertype");
        try (final SqlSession session = factory.openSession()) {
            return getMapper(session).findByUrlTypeVersion(url, layertype, version);
        }
    }

    /**
     * Inserts or updates a capabilities XML in database
     * The rows in the table are UNIQUE (layertype, version, url)
     */
    public OskariLayerCapabilities save(final OskariLayerCapabilitiesDraft draft) {
        try (final SqlSession session = factory.openSession(false)) {
            final CapabilitiesMapper mapper = getMapper(session);

            String url = draft.getUrl();
            String type = draft.getLayertype();
            String version = draft.getVersion();

            // Check if a row already exists
            Long id = mapper.selectIdByUrlTypeVersion(url, type, version);
            if (id != null) {
                // Update
                mapper.updateData(id, draft.getData());
                session.commit();
                OskariLayerCapabilities updated = mapper.findById(id);
                LOG.info("Updated capabilities:", updated);
                return updated;
            } else {
                // Insert
                mapper.insert(draft);
                session.commit();
                OskariLayerCapabilities inserted = mapper.findByUrlTypeVersion(url, type, version);
                LOG.info("Inserted capabilities:", inserted);
                return inserted;
            }
        }
    }

    @Override
    protected List<OskariLayerCapabilities> getAllOlderThan(long maxAgeMs) {
        try (final SqlSession session = factory.openSession()) {
            final CapabilitiesMapper mapper = getMapper(session);
            final long time = System.currentTimeMillis() - maxAgeMs;
            final Timestamp ts = new Timestamp(time);
            LOG.debug("Getting all rows not updated since:", ts);
            List<OskariLayerCapabilities> list = mapper.findAllNotUpdatedSince(ts);
            LOG.debug("Found", list.size(), "row(s) not updated since:", ts);
            return list;
        }
    }

    @Override
    protected void updateMultiple(List<OskariLayerCapabilitiesDataUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        try (final SqlSession session = factory.openSession(ExecutorType.BATCH, false)) {
            final CapabilitiesMapper mapper = getMapper(session);
            for (OskariLayerCapabilitiesDataUpdate update : updates) {
                LOG.debug("Updating capabilities id:", update.id, "data:", update.data);
                mapper.updateData(update.id, update.data);
            }
            session.commit();
        }
    }

}
