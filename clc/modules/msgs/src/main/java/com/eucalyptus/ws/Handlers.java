/*************************************************************************
 * Copyright 2009-2013 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 *
 * This file may incorporate work covered under the following copyright
 * and permission notice:
 *
 *   Software License Agreement (BSD License)
 *
 *   Copyright (c) 2008, Regents of the University of California
 *   All rights reserved.
 *
 *   Redistribution and use of this software in source and binary forms,
 *   with or without modification, are permitted provided that the
 *   following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *   COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *   BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *   LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *   ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE. USERS OF THIS SOFTWARE ACKNOWLEDGE
 *   THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE LICENSED MATERIAL,
 *   COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS SOFTWARE,
 *   AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *   IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA,
 *   SANTA BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY,
 *   WHICH IN THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION,
 *   REPLACEMENT OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO
 *   IDENTIFIED, OR WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT
 *   NEEDED TO COMPLY WITH ANY SUCH LICENSES OR RIGHTS.
 ************************************************************************/

package com.eucalyptus.ws;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpMessageEncoder;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.handler.timeout.WriteTimeoutHandler;
import org.jboss.netty.util.HashedWheelTimer;
import com.eucalyptus.auth.principal.User;
import com.eucalyptus.binding.BindingManager;
import com.eucalyptus.bootstrap.Bootstrap;
import com.eucalyptus.bootstrap.Hosts;
import com.eucalyptus.component.ComponentId;
import com.eucalyptus.component.ComponentIds;
import com.eucalyptus.component.ComponentMessages;
import com.eucalyptus.component.Components;
import com.eucalyptus.component.ServiceOperations;
import com.eucalyptus.component.ServiceUris;
import com.eucalyptus.component.Topology;
import com.eucalyptus.component.annotation.ComponentMessage;
import com.eucalyptus.component.annotation.ServiceOperation;
import com.eucalyptus.component.id.Eucalyptus;
import com.eucalyptus.context.Contexts;
import com.eucalyptus.context.ServiceStateException;
import com.eucalyptus.crypto.util.SslSetup;
import com.eucalyptus.empyrean.ServiceTransitionType;
import com.eucalyptus.http.MappingHttpMessage;
import com.eucalyptus.http.MappingHttpRequest;
import com.eucalyptus.http.MappingHttpResponse;
import com.eucalyptus.records.Logs;
import com.eucalyptus.system.Ats;
import com.eucalyptus.util.Exceptions;
import com.eucalyptus.ws.handlers.BindingHandler;
import com.eucalyptus.ws.handlers.InternalWsSecHandler;
import com.eucalyptus.ws.handlers.QueryTimestampHandler;
import com.eucalyptus.ws.handlers.SoapMarshallingHandler;
import com.eucalyptus.ws.handlers.http.HttpUtils;
import com.eucalyptus.ws.handlers.http.NioHttpDecoder;
import com.eucalyptus.ws.protocol.AddressingHandler;
import com.eucalyptus.ws.protocol.SoapHandler;
import com.eucalyptus.ws.server.NioServerHandler;
import com.eucalyptus.ws.server.ServiceContextHandler;
import com.eucalyptus.ws.server.ServiceHackeryHandler;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import edu.ucsb.eucalyptus.msgs.BaseMessage;

public class Handlers {
  private static Logger                                      LOG                      = Logger.getLogger( Handlers.class );
  private static final ExecutionHandler                      pipelineExecutionHandler = new ExecutionHandler( new OrderedMemoryAwareThreadPoolExecutor( StackConfiguration.SERVER_POOL_MAX_THREADS, 0, 0 ) );
  private static final ExecutionHandler                      serviceExecutionHandler  = new ExecutionHandler( new OrderedMemoryAwareThreadPoolExecutor( StackConfiguration.SERVER_POOL_MAX_THREADS, 0, 0 ) );
  private static final ChannelHandler                        queryTimestampHandler    = new QueryTimestampHandler( );
  private static final ChannelHandler                        soapMarshallingHandler   = new SoapMarshallingHandler( );
  private static final ChannelHandler                        httpRequestEncoder       = new NioHttpRequestEncoder( );
  private static final ChannelHandler                        internalWsSecHandler     = new InternalWsSecHandler( );
  private static final ChannelHandler                        soapHandler              = new SoapHandler( );
  private static final ChannelHandler                        addressingHandler        = new AddressingHandler( );
  private static final ChannelHandler                        bindingHandler           = new BindingHandler( BindingManager.getDefaultBinding( ) );
  private static final HashedWheelTimer                      timer                    = new HashedWheelTimer( );                                                                                             //TODO:GRZE: configurable
                                                                                                                                                                                                              
  enum ServerPipelineFactory implements ChannelPipelineFactory {
    INSTANCE;
    @Override
    public ChannelPipeline getPipeline( ) throws Exception {
      ChannelPipeline pipeline = Channels.pipeline( );
      pipeline.addLast( "ssl", Handlers.newSslHandler( ) );
      pipeline.addLast( "decoder", Handlers.newHttpDecoder( ) );
      pipeline.addLast( "encoder", Handlers.newHttpResponseEncoder( ) );
      pipeline.addLast( "chunkedWriter", Handlers.newChunkedWriteHandler( ) );
      pipeline.addLast( "fence", Handlers.bootstrapFence( ) );
      pipeline.addLast( "pipeline-filter", Handlers.newNioServerHandler( ) );
      if ( StackConfiguration.ASYNC_PIPELINE ) {
        pipeline.addLast( "async-pipeline-execution-handler", Handlers.pipelineExecutionHandler( ) );
      }
      return pipeline;
    }
    
  }
  
  private static NioServerHandler newNioServerHandler( ) {
    return new NioServerHandler( );
  }
  
  private static ChannelHandler newChunkedWriteHandler( ) {
    return new ChunkedWriteHandler( );
  }
  
  private static ChannelHandler newHttpResponseEncoder( ) {
    return new HttpResponseEncoder( );
  }
  
  private static ChannelHandler newHttpDecoder( ) {
    return new NioHttpDecoder( );
  }
  
  public static ChannelPipelineFactory serverPipelineFactory( ) {
    return ServerPipelineFactory.INSTANCE;
  }
  
  public static class NioHttpResponseDecoder extends HttpResponseDecoder {
    
    @Override
    protected HttpMessage createMessage( final String[] strings ) {
      return new MappingHttpResponse( strings );//HttpVersion.valueOf(strings[2]), HttpMethod.valueOf(strings[0]), strings[1] );
    }
  }
  
  @ChannelPipelineCoverage( "all" )
  public static class NioHttpRequestEncoder extends HttpMessageEncoder {
    
    public NioHttpRequestEncoder( ) {
      super( );
    }
    
    @Override
    protected void encodeInitialLine( final ChannelBuffer buf, final HttpMessage message ) throws Exception {
      final MappingHttpRequest request = ( MappingHttpRequest ) message;
      buf.writeBytes( request.getMethod( ).toString( ).getBytes( "ASCII" ) );
      buf.writeByte( HttpUtils.SP );
      buf.writeBytes( request.getServicePath( ).getBytes( "ASCII" ) );
      buf.writeByte( HttpUtils.SP );
      buf.writeBytes( request.getProtocolVersion( ).toString( ).getBytes( "ASCII" ) );
      buf.writeBytes( HttpUtils.CRLF );
    }
  }
  
  @ChannelPipelineCoverage( "all" )
  enum BootstrapStateCheck implements ChannelUpstreamHandler {
    INSTANCE;
    
    @Override
    public void handleUpstream( final ChannelHandlerContext ctx, final ChannelEvent e ) throws Exception {
      if ( !Bootstrap.isFinished( ) ) {
        //TODO:GRZE: do nothing for the moment, not envouh info here.
//        throw new ServiceNotReadyException( "System has not yet completed booting." );
        ctx.sendUpstream( e );
      } else {
        ctx.sendUpstream( e );
      }
    }
    
  }
  
  public static ChannelHandler bootstrapFence( ) {
    return BootstrapStateCheck.INSTANCE;
  }
  
  public static Map<String, ChannelHandler> channelMonitors( final TimeUnit unit, final int timeout ) {
    return new HashMap<String, ChannelHandler>( 3 ) {
      {
        this.put( "idlehandler", new IdleStateHandler( Handlers.timer, timeout, timeout, timeout, unit ) );
        this.put( "readTimeout", new ReadTimeoutHandler( Handlers.timer, timeout, unit ) );
        this.put( "writeTimeout", new WriteTimeoutHandler( Handlers.timer, timeout, unit ) );
      }
    };
  }
  
  public static ChannelHandler newSslHandler( ) {
    return new NioSslHandler( );
  }
  
  public static ChannelHandler newHttpResponseDecoder( ) {
    return new NioHttpResponseDecoder( );
  }
  
  public static ChannelHandler newHttpChunkAggregator( ) {
    return new HttpChunkAggregator( StackConfiguration.CLIENT_HTTP_CHUNK_BUFFER_MAX );
  }
  
  public static ChannelHandler addressingHandler( ) {//caching
    return addressingHandler;
  }
  
  public static ChannelHandler newAddressingHandler( final String addressingPrefix ) {//caching
    return new AddressingHandler( addressingPrefix );
  }
  
  private static final LoadingCache<String, BindingHandler> bindingHandlers = CacheBuilder.newBuilder().build(
    new CacheLoader<String, BindingHandler>() {
      @Override
      public BindingHandler load( String bindingName ) {
        String maybeBindingName = "";
        if ( BindingManager.isRegisteredBinding( bindingName ) ) {
          return new BindingHandler( BindingManager.getBinding( bindingName ) );
        } else if ( BindingManager.isRegisteredBinding( maybeBindingName = BindingManager.sanitizeNamespace( bindingName ) ) ) {
          return new BindingHandler( BindingManager.getBinding( maybeBindingName ) );
        } else {
          throw Exceptions.trace( "Failed to find registerd binding for name: " + bindingName
                                  + ".  Also tried looking for sanitized name: "
                                  + maybeBindingName );
      }
    }
  });
  
  public static ChannelHandler bindingHandler( ) {
    return bindingHandler;
  }
  
  public static ChannelHandler bindingHandler( final String bindingName ) {
    return bindingHandlers.getUnchecked( bindingName );
  }
  
  public static ChannelHandler httpRequestEncoder( ) {
    return httpRequestEncoder;
  }
  
  public static ChannelHandler soapMarshalling( ) {
    return soapMarshallingHandler;
  }
  
  public static ChannelHandler soapHandler( ) {
    return soapHandler;
  }
  
  @ChannelPipelineCoverage( "one" )
  private static class NioSslHandler extends SslHandler {
    private final AtomicBoolean first = new AtomicBoolean( true );
    
    NioSslHandler( ) {
      super( SslSetup.getServerEngine( ) );
    }
    
    private static List<String> httpVerbPrefix = Lists.newArrayList( HttpMethod.CONNECT.getName( ).substring( 0, 3 ),
                                                                     HttpMethod.GET.getName( ).substring( 0, 3 ),
                                                                     HttpMethod.PUT.getName( ).substring( 0, 3 ),
                                                                     HttpMethod.POST.getName( ).substring( 0, 3 ),
                                                                     HttpMethod.HEAD.getName( ).substring( 0, 3 ),
                                                                     HttpMethod.OPTIONS.getName( ).substring( 0, 3 ),
                                                                     HttpMethod.DELETE.getName( ).substring( 0, 3 ),
                                                                     HttpMethod.TRACE.getName( ).substring( 0, 3 ) );
    
    private static boolean maybeSsl( final ChannelBuffer buffer ) {
      buffer.markReaderIndex( );
      final StringBuffer sb = new StringBuffer( );
      for ( int lineLength = 0; lineLength++ < 3; sb.append( ( char ) buffer.readByte( ) ) );
      buffer.resetReaderIndex( );
      return !httpVerbPrefix.contains( sb.toString( ) );
    }
    
    @Override
    public void handleUpstream( final ChannelHandlerContext ctx, final ChannelEvent e ) throws Exception {
      Object o = null;
      if ( ( e instanceof MessageEvent )
           && this.first.compareAndSet( true, false )
           && ( ( o = ( ( MessageEvent ) e ).getMessage( ) ) instanceof ChannelBuffer )
           && !maybeSsl( ( ChannelBuffer ) o ) ) {
        ctx.getPipeline( ).removeFirst( );
        ctx.sendUpstream( e );
      } else {
        super.handleUpstream( ctx, e );
      }
    }
    
  }
  
  public static ChannelHandler internalServiceStateHandler( ) {
    return ServiceStateChecksHandler.INSTANCE;
  }
  
  @ChannelPipelineCoverage( "all" )
  public enum ServiceStateChecksHandler implements ChannelUpstreamHandler {
    INSTANCE {
      @Override
      public void handleUpstream( final ChannelHandlerContext ctx, final ChannelEvent e ) throws Exception {
        final MappingHttpRequest request = MappingHttpMessage.extractMessage( e );
        final BaseMessage msg = BaseMessage.extractMessage( e );
        if ( msg != null ) {
          try {
            final Class<? extends ComponentId> compClass = ComponentMessages.lookup( msg );
            ComponentId compId = ComponentIds.lookup( compClass );
            if ( compId.isAlwaysLocal( ) || Topology.isEnabledLocally( compClass ) ) {
              ctx.sendUpstream( e );
            } else {
              Handlers.sendRedirect( ctx, e, compClass, request );
            }
          } catch ( final NoSuchElementException ex ) {
            LOG.warn( "Failed to find reverse component mapping for message type: " + msg.getClass( ) );
            ctx.sendUpstream( e );
          } catch ( final Exception ex ) {
            Logs.extreme( ).error( ex, ex );
            ctx.sendUpstream( e );
          }
        } else {
          ctx.sendUpstream( e );
        }
      }
      
    }
  }
  
  public static ChannelHandler internalEpochHandler( ) {
    return MessageEpochChecks.INSTANCE;
  }
  
  @ChannelPipelineCoverage( "all" )
  enum MessageEpochChecks implements ChannelUpstreamHandler {
    INSTANCE {
      @Override
      public void handleUpstream( final ChannelHandlerContext ctx, final ChannelEvent e ) throws Exception {
        final MappingHttpRequest request = MappingHttpMessage.extractMessage( e );
        final BaseMessage msg = BaseMessage.extractMessage( e );
        if ( msg != null ) {
          try {
            if ( msg instanceof ServiceTransitionType && !Hosts.isCoordinator( ) ) {
              //TODO:GRZE: extra epoch check and redirect
              Topology.touch( ( ServiceTransitionType ) msg );
              ctx.sendUpstream( e );
            } else if ( Topology.check( msg ) ) {
              ctx.sendUpstream( e );
            } else {
              final Class<? extends ComponentId> compClass = ComponentMessages.lookup( msg );
              Handlers.sendRedirect( ctx, e, compClass, request );
            }
          } catch ( final Exception ex ) {
            Logs.extreme( ).error( ex, ex );
            ctx.sendUpstream( e );
          }
        }
      }
    };
    
  }

  @ChannelPipelineCoverage( "one" )
  private static final class ComponentMessageCheckHandler implements ChannelUpstreamHandler {
    @Nullable
    private final Class<? extends ComponentId> componentIdClass;

    private ComponentMessageCheckHandler( final Class<? extends ComponentId> componentIdClass ) {
      this.componentIdClass = componentIdClass;
    }

    @Override
    public void handleUpstream( final ChannelHandlerContext channelHandlerContext,
                                final ChannelEvent channelEvent ) throws Exception {
      if ( channelEvent instanceof MessageEvent && componentIdClass != null ) {
        final BaseMessage message = BaseMessage.extractMessage( channelEvent );
        final ComponentMessage componentMessage = message==null ? null :
            Ats.inClassHierarchy( message.getClass() ).get( ComponentMessage.class );
        if ( message != null && (componentMessage == null || !componentIdClass.equals( componentMessage.value() ) ) ) {
          LOG.warn( String.format("Message %s does not match pipeline component %s",
              message.getClass(),
              componentIdClass.getSimpleName() ) );

          final MappingHttpMessage mappingHttpMessage = MappingHttpMessage.extractMessage( channelEvent );
          final BaseMessage baseMessage = BaseMessage.extractMessage( channelEvent );
          if ( baseMessage != null ) {
            Contexts.clear( Contexts.lookup( baseMessage.getCorrelationId()) );
          }
          channelHandlerContext.getChannel( ).write( new MappingHttpResponse(
              mappingHttpMessage==null ? HttpVersion.HTTP_1_1  : mappingHttpMessage.getProtocolVersion( ),
              HttpResponseStatus.BAD_REQUEST ) );
          return;
        }
      }
      channelHandlerContext.sendUpstream( channelEvent );
    }
  }

  static void sendRedirect( final ChannelHandlerContext ctx, final ChannelEvent e, final Class<? extends ComponentId> compClass, final MappingHttpRequest request ) {
    e.getFuture( ).cancel( );
    String redirectUri = null;
    if ( Topology.isEnabled( compClass ) ) {//have an enabled service, lets use that 
      final URI serviceUri = ServiceUris.remote( Topology.lookup( compClass ) );
      redirectUri = serviceUri.toASCIIString( ) + request.getServicePath().replace( serviceUri.getPath( ), "" );
    } else if ( Topology.isEnabled( Eucalyptus.class ) ) {//can't find service info, redirect via clc master
      final URI serviceUri = ServiceUris.remote( Topology.lookup( Eucalyptus.class ) );
      redirectUri = serviceUri.toASCIIString( ).replace( Eucalyptus.INSTANCE.getServicePath( ), "" ) + request.getServicePath().replace( serviceUri.getPath( ), "" );
    }
    HttpResponse response = null;
    if ( redirectUri == null || isRedirectLoop( request, redirectUri ) ) {
      response = new DefaultHttpResponse( HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE );
      if ( Logs.isDebug( ) ) {
        String errorMessage = "Failed to lookup service for " + Components.lookup( compClass ).getName( )
          + " for path "
          + request.getServicePath()
          + ".\nCurrent state: \n\t"
          + Joiner.on( "\n\t" ).join( Topology.enabledServices( ) );
        byte[] errorBytes = Exceptions.string( new ServiceStateException( errorMessage ) ).getBytes( );
        response.setContent( ChannelBuffers.wrappedBuffer( errorBytes ) );
      }
    } else {
      response = new DefaultHttpResponse( HttpVersion.HTTP_1_1, HttpResponseStatus.TEMPORARY_REDIRECT );
      if( request.getQuery() != null ) {
        redirectUri += "?" + request.getQuery( );
      }
      response.setHeader( HttpHeaders.Names.LOCATION, redirectUri );
    }
    response.setHeader( HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE );
    if ( ctx.getChannel( ).isConnected( ) ) {
      ctx.getChannel( )
          .write( response )
          .addListener( ChannelFutureListener.CLOSE );
    }
  }
  
  public static ChannelHandler internalOnlyHandler( ) {
    return InternalOnlyHandler.INSTANCE;
  }
  
  @ChannelPipelineCoverage( "all" )
  enum InternalOnlyHandler implements ChannelUpstreamHandler {
    INSTANCE;
    @Override
    public void handleUpstream( final ChannelHandlerContext ctx, final ChannelEvent e ) throws Exception {
      final MappingHttpMessage request = MappingHttpMessage.extractMessage( e );
      final BaseMessage msg = BaseMessage.extractMessage( e );
      if ( ( request != null ) && ( msg != null ) ) {
        final User user = Contexts.lookup( request.getCorrelationId( ) ).getUser( );
        if ( user.isSystemAdmin( ) || ServiceOperations.isUserOperation( msg ) ) {
          ctx.sendUpstream( e );
        } else {
          Contexts.clear( Contexts.lookup( msg.getCorrelationId( ) ) );
          ctx.getChannel( ).write( new MappingHttpResponse( request.getProtocolVersion( ), HttpResponseStatus.FORBIDDEN ) );
        }
      } else {
        ctx.sendUpstream( e );
      }
    }
    
  }

  public static void addComponentHandlers( final Class<? extends ComponentId> componentIdClass,
                                           final ChannelPipeline pipeline ) {
    pipeline.addLast( "msg-component-check", new ComponentMessageCheckHandler( componentIdClass ) );
  }

  public static void addSystemHandlers( final ChannelPipeline pipeline ) {
    pipeline.addLast( "service-state-check", internalServiceStateHandler( ) );
    pipeline.addLast( "service-specific-mangling", ServiceHackeryHandler.INSTANCE );
    if ( StackConfiguration.ASYNC_OPERATIONS ) {
      pipeline.addLast( "async-operations-execution-handler", serviceExecutionHandler( ) );
    }
    pipeline.addLast( "service-sink", new ServiceContextHandler( ) );
  }
  
  public static void addInternalSystemHandlers( ChannelPipeline pipeline ) {
    pipeline.addLast( "internal-only-restriction", internalOnlyHandler( ) );
    pipeline.addLast( "msg-epoch-check", internalEpochHandler( ) );
  }
  
  public static ExecutionHandler pipelineExecutionHandler( ) {
    return pipelineExecutionHandler;
  }
  
  public static ExecutionHandler serviceExecutionHandler( ) {
    return serviceExecutionHandler;
  }
  
  public static ChannelHandler queryTimestamphandler( ) {
    return queryTimestampHandler;
  }
  
  public static ChannelHandler internalWsSecHandler( ) {
    return internalWsSecHandler;
  }

  private static boolean isRedirectLoop( final MappingHttpRequest request,
                                         final String uri )  {
    final String requestHost = request.getHeader( HttpHeaders.Names.HOST );
    final String requestPath = request.getServicePath( );
    return
        requestHost != null &&
        requestPath != null &&
        uri.contains( requestHost + requestPath );
  }
}
