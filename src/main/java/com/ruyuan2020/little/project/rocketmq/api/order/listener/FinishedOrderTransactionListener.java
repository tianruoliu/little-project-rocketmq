package com.ruyuan2020.little.project.rocketmq.api.order.listener;

import com.alibaba.fastjson.JSON;
import com.ruyuan2020.little.project.rocketmq.api.order.dto.OrderInfoDTO;
import com.ruyuan2020.little.project.rocketmq.api.order.enums.OrderStatusEnum;
import com.ruyuan2020.little.project.rocketmq.api.order.service.OrderEventInformManager;
import com.ruyuan2020.little.project.rocketmq.api.order.service.OrderService;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 退房订单事务消息Listener
 *
 * @author ajin
 */
@Component
public class FinishedOrderTransactionListener implements TransactionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinishedOrderTransactionListener.class);

    /**
     * 订单Service组件
     */
    @Autowired
    private OrderService orderService;

    /**
     * 订单事件消息通知管理组件
     */
    @Autowired
    private OrderEventInformManager orderEventInformManager;

    /**
     * 执行本地事务
     * When send transactional prepare(half) message succeed, this method will be invoked to execute local transaction.
     *
     * @param msg Half(prepare) message
     * @param arg Custom business parameter
     * @return Transaction state
     */
    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        // TODO 可以通过状态模式来校验订单的流转和保存订单操作日志
        String       body         = new String(msg.getBody(), StandardCharsets.UTF_8);
        OrderInfoDTO orderInfoDTO = JSON.parseObject(body, OrderInfoDTO.class);
        String       orderNo      = orderInfoDTO.getOrderNo();
        String       phoneNumber  = orderInfoDTO.getPhoneNumber();

        try {
            // 修改订单状态
            orderService.updateOrderStatus(orderNo, OrderStatusEnum.FINISHED, phoneNumber);
            // 发送退房成功通知
            orderEventInformManager.informOrderFinishEvent(orderInfoDTO);

            // 成功 提交prepare消息
            LOGGER.info("finished order local transaction execute success, commit orderNo:{}", orderNo);
            return LocalTransactionState.COMMIT_MESSAGE;
        } catch (Exception e) {
            // 执行本地事务失败 回滚prepare消息
            LOGGER.info("finished order local transaction execute fail rollback orderNo:{}", orderNo);
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }

    }

    /**
     * 检查本地事务  如果由于各种原因导致mq没收到commit或者rollback消息回调检查本地事务结果
     *
     * @param msg Check message
     * @return Transaction state
     */
    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        String       body         = new String(msg.getBody(), StandardCharsets.UTF_8);
        OrderInfoDTO orderInfoDTO = JSON.parseObject(body, OrderInfoDTO.class);
        String       orderNo      = orderInfoDTO.getOrderNo();
        String       phoneNumber  = orderInfoDTO.getPhoneNumber();
        LOGGER.info("callback check finished order local transaction status orderNo:{}", orderNo);

        // 根据订单状态 判断本地退房事务是否执行成功
        Integer orderStatus = orderService.getOrderStatus(orderNo, phoneNumber);
        try {
            if (Objects.equals(orderStatus, OrderStatusEnum.FINISHED.getStatus())) {
                LOGGER.info("finished order local transaction check result success commit orderNo:{}", orderNo);
                return LocalTransactionState.COMMIT_MESSAGE;
            }else {
                LOGGER.info("finished order local transaction check result fail rollback orderNo:{}", orderNo);
                return LocalTransactionState.ROLLBACK_MESSAGE;
            }
        } catch (Exception e) {
            // 查询订单状态失败
            LOGGER.info("finished order local transaction check result fail rollback orderNo:{}", orderNo);
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }

    }
}
