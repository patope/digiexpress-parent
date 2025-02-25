package io.digiexpress.client.spi;

import static io.digiexpress.client.spi.support.MainBranch.HEAD_NAME;

import java.io.IOException;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.dialob.client.api.DialobClient;
import io.dialob.client.pgsql.PgSqlDialobStore;
import io.dialob.client.spi.DialobClientImpl;
import io.dialob.client.spi.event.EventPublisher;
import io.dialob.client.spi.event.QuestionnaireEventPublisher;
import io.dialob.client.spi.function.AsyncFunctionInvoker;
import io.dialob.client.spi.function.FunctionRegistryImpl;
import io.dialob.client.spi.store.ImmutableDialobStoreConfig;
import io.dialob.client.spi.support.OidUtils;
import io.dialob.rule.parser.function.DefaultFunctions;
import io.dialob.rule.parser.function.FunctionRegistry;
import io.digiexpress.client.api.AssetEnvir;
import io.digiexpress.client.api.AssetExecutor.DialobExecutor;
import io.digiexpress.client.api.AssetExecutor.HdesExecutor;
import io.digiexpress.client.api.AssetExecutor.ProcessExecutor;
import io.digiexpress.client.api.AssetExecutor.StencilExecutor;
import io.digiexpress.client.api.AssetExecutorEntity.ProcessState;
import io.digiexpress.client.api.Client;
import io.digiexpress.client.api.ClientCache;
import io.digiexpress.client.api.ClientQuery;
import io.digiexpress.client.api.ImmutableClientConfig;
import io.digiexpress.client.spi.executors.DialobExecutorImpl;
import io.digiexpress.client.spi.executors.HdesExecutorImpl;
import io.digiexpress.client.spi.executors.ProcessExecutorImpl;
import io.digiexpress.client.spi.executors.StencilExecutorImpl;
import io.digiexpress.client.spi.store.ClientStorePostgreSQL;
import io.digiexpress.client.spi.store.ImmutableDocDBConfig;
import io.digiexpress.client.spi.support.ServiceAssert;
import io.resys.hdes.client.api.HdesClient;
import io.resys.hdes.client.spi.HdesClientImpl;
import io.resys.hdes.client.spi.ThenaStore;
import io.resys.hdes.client.spi.config.HdesClientConfig.DependencyInjectionContext;
import io.resys.hdes.client.spi.config.HdesClientConfig.ServiceInit;
import io.resys.hdes.client.spi.store.ImmutableThenaConfig;
import io.resys.thena.docdb.api.DocDB;
import io.thestencil.client.api.StencilClient;
import io.thestencil.client.spi.StencilClientImpl;
import io.thestencil.client.spi.serializers.ZoeDeserializer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;



@Slf4j
@RequiredArgsConstructor
public class ClientImpl implements Client {
  private final ClientConfig config;
  
  @Override
  public TenantBuilder tenant() {
    return new TenantBuilderImpl(config, config.getDocDb());
  }
  @Override
  public AssetEnvirBuilder envir() {
    return new AssetEnvirBuilderImpl(config);
  }
  @Override
  public ClientConfig getConfig() {
    return config;
  }
  @Override
  public ClientQuery getQuery() {
    return new ClientQueryImpl(config);
  }
  @Override
  public AssetExecutorBuilder executor(AssetEnvir envir) {
    return new AssetExecutorBuilder() {
      @Override public StencilExecutor stencil() { return new StencilExecutorImpl(config, envir); }
      @Override public HdesExecutor hdes(ProcessState state) { return new HdesExecutorImpl(config, state, envir); }
      @Override public DialobExecutor dialob(ProcessState state) { return new DialobExecutorImpl(config, state, envir); }
      @Override public ProcessExecutor process(String nameOrId) { return new ProcessExecutorImpl(config, nameOrId, envir); }
    };
  }
  public static Builder builder() {
    return new Builder();
  }
  @Data @Getter(AccessLevel.NONE)
  @Accessors(fluent = true, chain = true)
  public static class Builder {
    protected DocDB doc;
    protected ObjectMapper om;
    protected String headName = HEAD_NAME;
    protected ClientCache cache;
    protected String repoStencil;
    protected String repoHdes;
    protected String repoDialob;
    protected String repoService;
    protected Supplier<String> repoAuthor;
    
    protected DependencyInjectionContext hdesDjc;
    protected ServiceInit hdesServiceInit;
    protected QuestionnaireEventPublisher dialobEventPub;
    protected FunctionRegistry dialobFr;
    
    public ClientImpl build() {
      ServiceAssert.notNull(doc, () -> "doc: DocDB must be defined!");
      ServiceAssert.notNull(om, () -> "om: ObjectMapper must be defined!");
      ServiceAssert.notNull(repoStencil, () -> "repoStencil: string must be defined!");
      ServiceAssert.notNull(repoDialob, () -> "repoDialob: string must be defined!");
      ServiceAssert.notNull(repoHdes, () -> "repoHdes: string must be defined!");
      ServiceAssert.notNull(repoService, () -> "repoHdes: string must be defined!");
      
      ServiceAssert.notNull(hdesDjc, () -> "hdesDjc: DependencyInjectionContext must be defined!");
      ServiceAssert.notNull(hdesServiceInit, () -> "hdesServiceInit: ServiceInit must be defined!");
      
      ServiceAssert.notNull(dialobEventPub, () -> "dialobEventPub: QuestionnaireEventPublisher must be defined!");
      ServiceAssert.notNull(dialobFr, () -> "dialobFr: FunctionRegistry must be defined!");

      if(repoAuthor == null) {
        repoAuthor = () -> "not-configured";
      }
      
      if(cache == null) {
        this.cache = ClientEhCache.builder().build(repoService);
      }

      final var parser = new ParserImpl(om);
      final var config = ImmutableClientConfig.builder()
          .store(new ClientStorePostgreSQL(ImmutableDocDBConfig.builder()
              .authorProvider(() -> repoAuthor.get())
              .headName(headName).repoName(repoService)
              .gid((type) -> OidUtils.gen())
              .deserializer(new io.digiexpress.client.spi.store.DocDBDeserializer(om))
              .serializer((entity) -> applyOm((om) -> om.writeValueAsString(io.digiexpress.client.api.ImmutableStoreEntity.builder().from(entity).build())))
              .client(doc)
              .parser(parser)
              .build()))
          .cache(cache)
          .archiver(new ArchiverImpl(parser))
          .stencil(stencil())
          .dialob(dialob())
          .hdes(hdes())
          .docDb(doc)
          .parser(parser)
          .build();
      
      return new ClientImpl(config);
    }
    
    protected StencilClient stencil() {
      return StencilClientImpl.builder()
        .config((builder) -> builder
          .repoName(repoStencil)
          .headName(headName)
          .client(doc)
          .objectMapper(om)
          .deserializer(new ZoeDeserializer(om))
          .serializer((entity) -> applyOm((om) -> om.writeValueAsString(entity)))
          .gidProvider((type) -> OidUtils.gen())
          .authorProvider(() -> repoAuthor.get()))
        .build();  
    }
    
    protected HdesClient hdes() {
      final var config = ImmutableThenaConfig.builder()
        .client(doc)
        .repoName(repoHdes).headName(headName)
        .gidProvider((type) -> OidUtils.gen())
        .serializer((entity) -> applyOm((om) -> om.writeValueAsString(io.resys.hdes.client.api.ImmutableStoreEntity.builder().from(entity).hash("").build())))
        .deserializer(new io.resys.hdes.client.spi.store.BlobDeserializer(om))
        .authorProvider(() -> repoAuthor.get())
        .build();
      return HdesClientImpl.builder()
        .objectMapper(om)
        .store(new ThenaStore(config))
        .dependencyInjectionContext(hdesDjc)
        .serviceInit(hdesServiceInit)
        .build();  
    }
    
    protected DialobClient dialob() {
      final var asyncFunctionInvoker = new AsyncFunctionInvoker(dialobFr);
      final var config = ImmutableDialobStoreConfig.builder()
          .client(doc).repoName(repoDialob).headName(headName)
          .gidProvider((type) -> OidUtils.gen())
          .serializer((entity) -> applyOm((om) -> om.writeValueAsString(io.dialob.client.api.ImmutableStoreEntity.builder().from(entity).build())))
          .deserializer(new io.dialob.client.spi.store.BlobDeserializer(om))
          .authorProvider(() -> repoAuthor.get())
          .build();
      return DialobClientImpl.builder()
          .store(new PgSqlDialobStore(config))
          .objectMapper(om)
          .eventPublisher(dialobEventPub)
          .asyncFunctionInvoker(asyncFunctionInvoker)
          .functionRegistry(dialobFr)
          .build();
    }
    
    protected <T> T applyOm(DoInOm<T> callback) {
      try {
        return callback.apply(this.om);
      } catch (IOException e) {
        throw new RuntimeException(e.getMessage(), e);
      }
    }
    @FunctionalInterface
    protected interface DoInOm<T> {
      T apply(ObjectMapper om) throws IOException;
    }
    
    public Builder defaultObjectMapper() {
      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.registerModule(new GuavaModule());
      objectMapper.registerModule(new JavaTimeModule());
      objectMapper.registerModule(new Jdk8Module());
      om = objectMapper;
      return this;
    }
    
    public Builder defaultDialobEventPub() {
      final var  publisher = new EventPublisher() {
        @Override
        public void publish(Event event) {
          log.debug("dialob event publisher: " + event);
        }
      };
      this.dialobEventPub = new QuestionnaireEventPublisher(publisher);
      return this;
    }
    public Builder defaultDialobFr() {
      this.dialobFr = new FunctionRegistryImpl();
      final var defaultFunctions = new DefaultFunctions(dialobFr);
      log.debug("dialob default functions: " + defaultFunctions.getClass().getCanonicalName());
      return this;
    }
    public Builder defaultHdesDjc() {
      this.hdesDjc = new DependencyInjectionContext() {
        @Override
        public <T> T get(Class<T> type) {
          return null;
        }
      };
      return this;
    }
    public Builder defaultHdesServiceInit() {
      this.hdesServiceInit = new ServiceInit() {
        @Override
        public <T> T get(Class<T> type) {
          try {
            return type.getDeclaredConstructor().newInstance();
          } catch(Exception e) {
            throw new RuntimeException(e.getMessage(), e);
          }
        }
      };
      return this;
    }
  }
}
