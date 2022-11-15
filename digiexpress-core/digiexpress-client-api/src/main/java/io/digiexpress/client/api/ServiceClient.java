package io.digiexpress.client.api;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

import org.immutables.value.Value;

import io.dialob.api.questionnaire.Questionnaire;
import io.dialob.client.api.DialobClient;
import io.digiexpress.client.api.ServiceDocument.ServiceReleaseDocument;
import io.resys.hdes.client.api.HdesClient;
import io.resys.hdes.client.api.programs.FlowProgram.FlowResult;
import io.resys.thena.docdb.api.DocDB;
import io.smallrye.mutiny.Uni;
import io.thestencil.client.api.MigrationBuilder.LocalizedSite;
import io.thestencil.client.api.StencilClient;

public interface ServiceClient {
  ServiceEnvirBuilder envir();
  ServiceRepoBuilder repo();
  ServiceExecutorBuilder executor(ServiceEnvir envir);
  ServiceClientConfig getConfig();
  QueryFactory getQuery();
  
  interface ServiceClientException {}

  interface ServiceExecutorBuilder {
    ProcessExecutor process(String nameOrId);
    DialobExecutor dialob(ProcessState state);
    HdesExecutor hdes(ProcessState state);
    StencilExecutor stencil();
  }

  // returns new process instance and new fill session
  interface ProcessExecutor {
    ProcessExecutor targetDate(LocalDateTime now);
    ProcessExecutor actions(Map<String, Serializable> initVariables);
    ProcessExecutor action(String variableName, Serializable variableValue);
    Execution<ProcessState> build();
  }

  // continues fill
  interface DialobExecutor {
    DialobExecutor store(QuestionnaireStore store);
    DialobExecutor actions(io.dialob.api.proto.Actions userActions);
    Execution<ExecutionDialobBody> build();
  }
  
  interface HdesExecutor {
    HdesExecutor store(QuestionnaireStore store);
    HdesExecutor targetDate(LocalDateTime targetDate);
    Execution<ExecutionHdesBody> build();
  }

  // returns stencil content
  interface StencilExecutor {
    StencilExecutor targetDate(LocalDateTime targetDate);
    StencilExecutor locale(String locale);
    Execution<LocalizedSite> build();
  }
  
  
  @Value.Immutable
  interface Execution<T> {
    T getBody();
  }

  @Value.Immutable
  interface ExecutionDialobBody {
    ProcessState getState();
    io.dialob.api.questionnaire.Questionnaire getQuestionnaire();
    io.dialob.api.proto.Actions getActions();
  }
  
  @Value.Immutable
  interface ExecutionHdesBody {
    ProcessState getState();
    FlowResult getFlow();
  }

  interface ServiceRepoBuilder {
    ServiceRepoBuilder repoStencil(String repoStencil);
    ServiceRepoBuilder repoHdes(String repoHdes);
    ServiceRepoBuilder repoDialob(String repoDialob);
    ServiceRepoBuilder repoService(String repoService);

    Uni<ServiceClient> load();
    Uni<ServiceClient> create();
    ServiceClient build();
  }
  
  interface QuestionnaireStore {
    Questionnaire get(String questionnaireId);
  }
  
  interface ServiceEnvirBuilder {
    ServiceEnvirBuilder add(ServiceReleaseDocument release);
    ServiceEnvir build();
  }
  
  @Value.Immutable
  interface ServiceClientConfig {
    ServiceStore getStore();
    ServiceCache getCache();
    ServiceMapper getMapper();
    
    DialobClient getDialob();
    HdesClient getHdes();
    StencilClient getStencil();
    CompressionMapper getCompression();
    DocDB getDocDb();
  }
}
