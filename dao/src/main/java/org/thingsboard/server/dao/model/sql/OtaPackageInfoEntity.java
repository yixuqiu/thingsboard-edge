/**
 * Copyright © 2016-2021 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.dao.model.sql;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.OtaPackageInfo;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.ota.ChecksumAlgorithm;
import org.thingsboard.server.common.data.ota.OtaPackageType;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.OtaPackageId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.model.BaseSqlEntity;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.model.SearchTextEntity;
import org.thingsboard.server.dao.util.mapping.JsonStringType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.OTA_PACKAGE_CHECKSUM_ALGORITHM_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.OTA_PACKAGE_CHECKSUM_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.OTA_PACKAGE_CONTENT_TYPE_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.OTA_PACKAGE_DATA_SIZE_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.OTA_PACKAGE_DEVICE_PROFILE_ID_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.OTA_PACKAGE_FILE_NAME_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.OTA_PACKAGE_TABLE_NAME;
import static org.thingsboard.server.dao.model.ModelConstants.OTA_PACKAGE_TENANT_ID_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.OTA_PACKAGE_TILE_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.OTA_PACKAGE_TYPE_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.OTA_PACKAGE_URL_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.OTA_PACKAGE_VERSION_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.SEARCH_TEXT_PROPERTY;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@TypeDef(name = "json", typeClass = JsonStringType.class)
@Table(name = OTA_PACKAGE_TABLE_NAME)
public class OtaPackageInfoEntity extends BaseSqlEntity<OtaPackageInfo> implements SearchTextEntity<OtaPackageInfo> {

    @Column(name = OTA_PACKAGE_TENANT_ID_COLUMN)
    private UUID tenantId;

    @Column(name = OTA_PACKAGE_DEVICE_PROFILE_ID_COLUMN)
    private UUID deviceProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = OTA_PACKAGE_TYPE_COLUMN)
    private OtaPackageType type;

    @Column(name = OTA_PACKAGE_TILE_COLUMN)
    private String title;

    @Column(name = OTA_PACKAGE_VERSION_COLUMN)
    private String version;

    @Column(name = OTA_PACKAGE_URL_COLUMN)
    private String url;

    @Column(name = OTA_PACKAGE_FILE_NAME_COLUMN)
    private String fileName;

    @Column(name = OTA_PACKAGE_CONTENT_TYPE_COLUMN)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = OTA_PACKAGE_CHECKSUM_ALGORITHM_COLUMN)
    private ChecksumAlgorithm checksumAlgorithm;

    @Column(name = OTA_PACKAGE_CHECKSUM_COLUMN)
    private String checksum;

    @Column(name = OTA_PACKAGE_DATA_SIZE_COLUMN)
    private Long dataSize;

    @Type(type = "json")
    @Column(name = ModelConstants.OTA_PACKAGE_ADDITIONAL_INFO_COLUMN)
    private JsonNode additionalInfo;

    @Column(name = SEARCH_TEXT_PROPERTY)
    private String searchText;

    @Transient
    private boolean hasData;

    public OtaPackageInfoEntity() {
        super();
    }

    public OtaPackageInfoEntity(OtaPackageInfo firmware) {
        this.createdTime = firmware.getCreatedTime();
        this.setUuid(firmware.getUuidId());
        this.tenantId = firmware.getTenantId().getId();
        this.type = firmware.getType();
        if (firmware.getDeviceProfileId() != null) {
            this.deviceProfileId = firmware.getDeviceProfileId().getId();
        }
        this.title = firmware.getTitle();
        this.version = firmware.getVersion();
        this.url = firmware.getUrl();
        this.fileName = firmware.getFileName();
        this.contentType = firmware.getContentType();
        this.checksumAlgorithm = firmware.getChecksumAlgorithm();
        this.checksum = firmware.getChecksum();
        this.dataSize = firmware.getDataSize();
        this.additionalInfo = firmware.getAdditionalInfo();
    }

    public OtaPackageInfoEntity(UUID id, long createdTime, UUID tenantId, UUID deviceProfileId, OtaPackageType type, String title, String version,
                                String url, String fileName, String contentType, ChecksumAlgorithm checksumAlgorithm, String checksum, Long dataSize,
                                Object additionalInfo, boolean hasData) {
        this.id = id;
        this.createdTime = createdTime;
        this.tenantId = tenantId;
        this.deviceProfileId = deviceProfileId;
        this.type = type;
        this.title = title;
        this.version = version;
        this.url = url;
        this.fileName = fileName;
        this.contentType = contentType;
        this.checksumAlgorithm = checksumAlgorithm;
        this.checksum = checksum;
        this.dataSize = dataSize;
        this.hasData = hasData;
        this.additionalInfo = JacksonUtil.convertValue(additionalInfo, JsonNode.class);
    }

    @Override
    public String getSearchTextSource() {
        return title;
    }

    @Override
    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    @Override
    public OtaPackageInfo toData() {
        OtaPackageInfo firmware = new OtaPackageInfo(new OtaPackageId(id));
        firmware.setCreatedTime(createdTime);
        firmware.setTenantId(new TenantId(tenantId));
        if (deviceProfileId != null) {
            firmware.setDeviceProfileId(new DeviceProfileId(deviceProfileId));
        }
        firmware.setType(type);
        firmware.setTitle(title);
        firmware.setVersion(version);
        firmware.setUrl(url);
        firmware.setFileName(fileName);
        firmware.setContentType(contentType);
        firmware.setChecksumAlgorithm(checksumAlgorithm);
        firmware.setChecksum(checksum);
        firmware.setDataSize(dataSize);
        firmware.setAdditionalInfo(additionalInfo);
        firmware.setHasData(hasData);
        return firmware;
    }
}
