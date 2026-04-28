package src.alert_system.notification.infrastructure.statemachine;

import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import src.alert_system.notification.domain.enums.DeliveryEvent;
import src.alert_system.notification.domain.enums.DeliveryState;

import java.util.EnumSet;

@Configuration
@EnableStateMachineFactory(name = "deliveryStateMachineFactory")
public class DeliveryStateMachineConfig
        extends EnumStateMachineConfigurerAdapter<DeliveryState, DeliveryEvent> {

    @Override
    public void configure(StateMachineConfigurationConfigurer<DeliveryState, DeliveryEvent> config)
            throws Exception {
        config.withConfiguration()
                .autoStartup(false);
    }

    @Override
    public void configure(StateMachineStateConfigurer<DeliveryState, DeliveryEvent> states)
            throws Exception {
        states.withStates()
                .initial(DeliveryState.PENDING)
                .states(EnumSet.allOf(DeliveryState.class))
                .end(DeliveryState.SENT)
                .end(DeliveryState.DEAD);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<DeliveryState, DeliveryEvent> transitions)
            throws Exception {
        transitions
                // 대기 -> 발송중
                .withExternal()
                    .source(DeliveryState.PENDING).target(DeliveryState.PROCESSING)
                    .event(DeliveryEvent.DISPATCH)
                    .and()

                // 발송중 -> 발송완료
                .withExternal()
                    .source(DeliveryState.PROCESSING).target(DeliveryState.SENT)
                    .event(DeliveryEvent.SEND_SUCCESS)
                    .and()

                // 발송중 -> 발송 실패
                .withExternal()
                    .source(DeliveryState.PROCESSING).target(DeliveryState.FAILED)
                    .event(DeliveryEvent.SEND_FAILURE)
                    .and()

                // 발송중 -> 발송 불가 : 재시도 불가
                .withExternal()
                    .source(DeliveryState.PROCESSING).target(DeliveryState.DEAD)
                    .event(DeliveryEvent.EXHAUST)
                    .and()

                // 발송 실패 -> 발송중 : 재시도
                .withExternal()
                    .source(DeliveryState.FAILED).target(DeliveryState.PROCESSING)
                    .event(DeliveryEvent.RETRY)
                    .and()

                // 발송중 -> 대기 : 복구(추후 구현)
                .withExternal()
                    .source(DeliveryState.PROCESSING).target(DeliveryState.PENDING)
                    .event(DeliveryEvent.RECOVER)
                    .and()

                // 발송 불가 -> 대기중 : 수동처리(추후 구현)
                .withExternal()
                    .source(DeliveryState.DEAD).target(DeliveryState.PENDING)
                    .event(DeliveryEvent.MANUAL_RETRY);
    }
}
