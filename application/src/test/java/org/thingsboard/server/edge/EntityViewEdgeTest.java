/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.server.edge;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Assert;
import org.junit.Test;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.EntityViewInfo;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.gen.edge.v1.EdgeEntityType;
import org.thingsboard.server.gen.edge.v1.EntityViewUpdateMsg;
import org.thingsboard.server.gen.edge.v1.EntityViewsRequestMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.edge.v1.UplinkMsg;
import org.thingsboard.server.gen.edge.v1.UplinkResponseMsg;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class EntityViewEdgeTest extends AbstractEdgeTest {

    @Test
    public void testEntityViews() throws Exception {
        // create entity view and assign to edge
        edgeImitator.expectMessageAmount(1);
        Device device = findDeviceByName("Edge Device 1");
        EntityView savedEntityView = saveEntityView("Edge EntityView 1", device.getId());
        doPost("/api/edge/" + edge.getUuidId()
                + "/entityView/" + savedEntityView.getUuidId(), EntityView.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        verifyEntityViewUpdateMsg(savedEntityView, device);

        // update entity view
        edgeImitator.expectMessageAmount(1);
        savedEntityView.setName("Edge EntityView 1 Updated");
        savedEntityView = doPost("/api/entityView", savedEntityView, EntityView.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        EntityViewUpdateMsg entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(savedEntityView.getName(), entityViewUpdateMsg.getName());

        // request entity view(s) for device
        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        EntityViewsRequestMsg.Builder entityViewsRequestBuilder = EntityViewsRequestMsg.newBuilder();
        entityViewsRequestBuilder.setEntityIdMSB(device.getUuidId().getMostSignificantBits());
        entityViewsRequestBuilder.setEntityIdLSB(device.getUuidId().getLeastSignificantBits());
        entityViewsRequestBuilder.setEntityType(device.getId().getEntityType().name());
        testAutoGeneratedCodeByProtobuf(entityViewsRequestBuilder);
        uplinkMsgBuilder.addEntityViewsRequestMsg(entityViewsRequestBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());
        verifyEntityViewUpdateMsg(savedEntityView, device);

        // unassign entity view from edge
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getUuidId()
                + "/entityView/" + savedEntityView.getUuidId(), EntityView.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(entityViewUpdateMsg.getIdMSB(), savedEntityView.getUuidId().getMostSignificantBits());
        Assert.assertEquals(entityViewUpdateMsg.getIdLSB(), savedEntityView.getUuidId().getLeastSignificantBits());

        // delete entity view - no messages expected
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/entityView/" + savedEntityView.getUuidId())
                .andExpect(status().isOk());
        Assert.assertFalse(edgeImitator.waitForMessages(1));

        // create entity view #2 and assign to edge
        edgeImitator.expectMessageAmount(1);
        savedEntityView = saveEntityView("Edge EntityView 2", device.getId());
        doPost("/api/edge/" + edge.getUuidId()
                + "/entityView/" + savedEntityView.getUuidId(), EntityView.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        verifyEntityViewUpdateMsg(savedEntityView, device);

        // assign entity view #2 to customer
        Customer customer = new Customer();
        customer.setTitle("Edge Customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);
        edgeImitator.expectMessageAmount(2);
        doPost("/api/customer/" + savedCustomer.getUuidId()
                + "/edge/" + edge.getUuidId(), Edge.class);
        Assert.assertTrue(edgeImitator.waitForMessages());

        edgeImitator.expectMessageAmount(1);
        doPost("/api/customer/" + savedCustomer.getUuidId()
                + "/entityView/" + savedEntityView.getUuidId(), EntityView.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(savedCustomer.getUuidId().getMostSignificantBits(), entityViewUpdateMsg.getCustomerIdMSB());
        Assert.assertEquals(savedCustomer.getUuidId().getLeastSignificantBits(), entityViewUpdateMsg.getCustomerIdLSB());

        // unassign entity view #2 from customer
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/customer/entityView/" + savedEntityView.getUuidId(), EntityView.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(
                new CustomerId(EntityId.NULL_UUID),
                new CustomerId(new UUID(entityViewUpdateMsg.getCustomerIdMSB(), entityViewUpdateMsg.getCustomerIdLSB())));

        // delete entity view #2 - messages expected
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/entityView/" + savedEntityView.getUuidId())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(savedEntityView.getUuidId().getMostSignificantBits(), entityViewUpdateMsg.getIdMSB());
        Assert.assertEquals(savedEntityView.getUuidId().getLeastSignificantBits(), entityViewUpdateMsg.getIdLSB());

    }

    @Test
    public void testSendEntityViewToCloud() throws Exception {
        Device device = findDeviceByName("Edge Device 1");

        UUID uuid = Uuids.timeBased();

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        EntityViewUpdateMsg.Builder entityViewUpdateMsgBuilder = EntityViewUpdateMsg.newBuilder();
        entityViewUpdateMsgBuilder.setIdMSB(uuid.getMostSignificantBits());
        entityViewUpdateMsgBuilder.setIdLSB(uuid.getLeastSignificantBits());
        entityViewUpdateMsgBuilder.setName("Edge EntityView 2");
        entityViewUpdateMsgBuilder.setType("test");
        entityViewUpdateMsgBuilder.setEntityType(EdgeEntityType.DEVICE);
        entityViewUpdateMsgBuilder.setEntityIdMSB(device.getUuidId().getMostSignificantBits());
        entityViewUpdateMsgBuilder.setEntityIdLSB(device.getUuidId().getLeastSignificantBits());
        entityViewUpdateMsgBuilder.setMsgType(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE);
        testAutoGeneratedCodeByProtobuf(entityViewUpdateMsgBuilder);
        uplinkMsgBuilder.addEntityViewUpdateMsg(entityViewUpdateMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);

        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());

        Assert.assertTrue(edgeImitator.waitForResponses());

        UplinkResponseMsg latestResponseMsg = edgeImitator.getLatestResponseMsg();
        Assert.assertTrue(latestResponseMsg.getSuccess());

        EntityView entityView = doGet("/api/entityView/" + uuid, EntityView.class);
        Assert.assertNotNull(entityView);
        Assert.assertEquals("Edge EntityView 2", entityView.getName());
    }

    @Test
    public void testSendEntityViewToCloudWithNameThatAlreadyExistsOnCloud() throws Exception {
        Device device = findDeviceByName("Edge Device 1");

        String entityViewOnCloudName = StringUtils.randomAlphanumeric(15);
        EntityView entityViewOnCloud = saveEntityView(entityViewOnCloudName, device.getId());

        UUID uuid = Uuids.timeBased();

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        EntityViewUpdateMsg.Builder entityViewUpdateMsgBuilder = EntityViewUpdateMsg.newBuilder();
        entityViewUpdateMsgBuilder.setIdMSB(uuid.getMostSignificantBits());
        entityViewUpdateMsgBuilder.setIdLSB(uuid.getLeastSignificantBits());
        entityViewUpdateMsgBuilder.setName(entityViewOnCloudName);
        entityViewUpdateMsgBuilder.setType("test");
        entityViewUpdateMsgBuilder.setEntityType(EdgeEntityType.DEVICE);
        entityViewUpdateMsgBuilder.setEntityIdMSB(device.getUuidId().getMostSignificantBits());
        entityViewUpdateMsgBuilder.setEntityIdLSB(device.getUuidId().getLeastSignificantBits());
        entityViewUpdateMsgBuilder.setMsgType(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE);
        testAutoGeneratedCodeByProtobuf(entityViewUpdateMsgBuilder);
        uplinkMsgBuilder.addEntityViewUpdateMsg(entityViewUpdateMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);

        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());

        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        Optional<EntityViewUpdateMsg> entityViewUpdateMsgOpt = edgeImitator.findMessageByType(EntityViewUpdateMsg.class);
        Assert.assertTrue(entityViewUpdateMsgOpt.isPresent());
        EntityViewUpdateMsg latestEntityViewUpdateMsg = entityViewUpdateMsgOpt.get();
        Assert.assertNotEquals(entityViewOnCloudName, latestEntityViewUpdateMsg.getName());

        UUID newEntityViewId = new UUID(latestEntityViewUpdateMsg.getIdMSB(), latestEntityViewUpdateMsg.getIdLSB());

        Assert.assertNotEquals(entityViewOnCloud.getId().getId(), newEntityViewId);

        EntityView entityView = doGet("/api/entityView/" + newEntityViewId, EntityView.class);
        Assert.assertNotNull(entityView);
        Assert.assertNotEquals(entityViewOnCloudName, entityView.getName());
    }

    @Test
    public void testSendDeleteEntityViewOnEdgeToCloud() throws Exception {
        Device device = findDeviceByName("Edge Device 1");
        EntityView savedEntityView = saveEntityViewOnCloudAndVerifyDeliveryToEdge(device);

        UplinkMsg.Builder upLinkMsgBuilder = UplinkMsg.newBuilder();
        EntityViewUpdateMsg.Builder entityViewDeleteMsgBuilder = EntityViewUpdateMsg.newBuilder();
        entityViewDeleteMsgBuilder.setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE);
        entityViewDeleteMsgBuilder.setIdMSB(savedEntityView.getUuidId().getMostSignificantBits());
        entityViewDeleteMsgBuilder.setIdLSB(savedEntityView.getUuidId().getLeastSignificantBits());
        testAutoGeneratedCodeByProtobuf(entityViewDeleteMsgBuilder);

        upLinkMsgBuilder.addEntityViewUpdateMsg(entityViewDeleteMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(upLinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(upLinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        EntityViewInfo entityViewInfo = doGet("/api/entityView/info/" + savedEntityView.getUuidId(), EntityViewInfo.class);
        Assert.assertNotNull(entityViewInfo);
        List<EntityViewInfo> edgeAssets = doGetTypedWithPageLink("/api/edge/" + edge.getUuidId() + "/entityViews?",
                new TypeReference<PageData<EntityViewInfo>>() {
                }, new PageLink(100)).getData();
        Assert.assertFalse(edgeAssets.contains(entityViewInfo));
    }

    private void verifyEntityViewUpdateMsg(EntityView entityView, Device device) throws InvalidProtocolBufferException {
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        EntityViewUpdateMsg entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(entityView.getType(), entityViewUpdateMsg.getType());
        Assert.assertEquals(entityView.getName(), entityViewUpdateMsg.getName());
        Assert.assertEquals(entityView.getUuidId().getMostSignificantBits(), entityViewUpdateMsg.getIdMSB());
        Assert.assertEquals(entityView.getUuidId().getLeastSignificantBits(), entityViewUpdateMsg.getIdLSB());
        Assert.assertEquals(device.getUuidId().getMostSignificantBits(), entityViewUpdateMsg.getEntityIdMSB());
        Assert.assertEquals(device.getUuidId().getLeastSignificantBits(), entityViewUpdateMsg.getEntityIdLSB());
        Assert.assertEquals(device.getId().getEntityType().name(), entityViewUpdateMsg.getEntityType().name());
        testAutoGeneratedCodeByProtobuf(entityViewUpdateMsg);
    }

    private EntityView saveEntityViewOnCloudAndVerifyDeliveryToEdge(Device device) throws Exception {
        // create entity view and assign to edge
        EntityView savedEntityView = saveEntityView(StringUtils.randomAlphanumeric(15), device.getId());
        edgeImitator.expectMessageAmount(1); // entity view message
        doPost("/api/edge/" + edge.getUuidId()
                + "/entityView/" + savedEntityView.getUuidId(), EntityView.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        Optional<EntityViewUpdateMsg> entityViewUpdateMsgOpt = edgeImitator.findMessageByType(EntityViewUpdateMsg.class);
        Assert.assertTrue(entityViewUpdateMsgOpt.isPresent());
        EntityViewUpdateMsg entityViewUpdateMsg = entityViewUpdateMsgOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(savedEntityView.getUuidId().getMostSignificantBits(), entityViewUpdateMsg.getIdMSB());
        Assert.assertEquals(savedEntityView.getUuidId().getLeastSignificantBits(), entityViewUpdateMsg.getIdLSB());
        return savedEntityView;
    }

    private EntityView saveEntityView(String name, DeviceId deviceId) {
        EntityView entityView = new EntityView();
        entityView.setName(name);
        entityView.setType("test");
        entityView.setEntityId(deviceId);
        return doPost("/api/entityView", entityView, EntityView.class);
    }

}
