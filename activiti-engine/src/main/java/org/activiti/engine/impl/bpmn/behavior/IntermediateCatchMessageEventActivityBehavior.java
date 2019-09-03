/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.engine.impl.bpmn.behavior;

import java.util.List;
import java.util.Optional;

import org.activiti.bpmn.model.MessageEventDefinition;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.event.impl.ActivitiEventBuilder;
import org.activiti.engine.history.DeleteReason;
import org.activiti.engine.impl.bpmn.parser.factory.MessageExecutionContext;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.EventSubscriptionEntity;
import org.activiti.engine.impl.persistence.entity.EventSubscriptionEntityManager;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.MessageEventSubscriptionEntity;

public class IntermediateCatchMessageEventActivityBehavior extends IntermediateCatchEventActivityBehavior {

  private static final long serialVersionUID = 1L;

  protected final MessageEventDefinition messageEventDefinition;
  protected final MessageExecutionContext messageExecutionContext;

  public IntermediateCatchMessageEventActivityBehavior(MessageEventDefinition messageEventDefinition,
                                                       MessageExecutionContext messageExecutionContext) {
    this.messageEventDefinition = messageEventDefinition;
    this.messageExecutionContext = messageExecutionContext;
  }

  public void execute(DelegateExecution execution) {
    CommandContext commandContext = Context.getCommandContext();
    
    String messageName = messageExecutionContext.getMessageName(execution);
    
    MessageEventSubscriptionEntity messageEvent = commandContext.getEventSubscriptionEntityManager()
                                                                .insertMessageEvent(messageName, 
                                                                                    ExecutionEntity.class.cast(execution));
    Optional<String> correlationKey = messageExecutionContext.getCorrelationKey(execution);

    correlationKey.ifPresent(messageEvent::setConfiguration);
    
    if (commandContext.getProcessEngineConfiguration().getEventDispatcher().isEnabled()) {
        commandContext.getProcessEngineConfiguration().getEventDispatcher()
                .dispatchEvent(ActivitiEventBuilder.createMessageWaitingEvent(execution,
                                                                              messageName,
                                                                              correlationKey.orElse(null)));
      }
  }

  @Override
  public void trigger(DelegateExecution execution, String triggerName, Object triggerData) {
    ExecutionEntity executionEntity = deleteMessageEventSubScription(execution);
    leaveIntermediateCatchEvent(executionEntity);
  }
  
  @Override
  public void eventCancelledByEventGateway(DelegateExecution execution) {
    deleteMessageEventSubScription(execution);
    Context.getCommandContext().getExecutionEntityManager().deleteExecutionAndRelatedData((ExecutionEntity) execution, 
        DeleteReason.EVENT_BASED_GATEWAY_CANCEL, false);
  }

  protected ExecutionEntity deleteMessageEventSubScription(DelegateExecution execution) {
    ExecutionEntity executionEntity = (ExecutionEntity) execution;
    String messageName = messageExecutionContext.getMessageName(execution);
    
    EventSubscriptionEntityManager eventSubscriptionEntityManager = Context.getCommandContext().getEventSubscriptionEntityManager();
    List<EventSubscriptionEntity> eventSubscriptions = executionEntity.getEventSubscriptions();
    for (EventSubscriptionEntity eventSubscription : eventSubscriptions) {
      // TODO use correlation key condition
      if (eventSubscription instanceof MessageEventSubscriptionEntity && eventSubscription.getEventName().equals(messageName)) {
        eventSubscriptionEntityManager.delete(eventSubscription);
      }
    }
    return executionEntity;
  }

  public MessageEventDefinition getMessageEventDefinition() {
    return messageEventDefinition;
  }

  public MessageExecutionContext getMessageExecutionContext() {
    return messageExecutionContext;
  }
  
}
