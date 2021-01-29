 
[toc]

```
graph LR
用户接收者-->netty
netty-->rabbitmq
```
保证每个用户获取到自己的消息，需要每个用户都订阅唯一的队列。

问题1：会产生大量的队列。

## 1.用户(取消)订阅

1. 定义Rabbitmq的direct类型的交换机
2. 定义用户的Rabbitmq队列
3. 将队列通过路由键绑定或解绑direct交换机

 
### 1.1. 代码逻辑

在yml文件添加mq的配置

```
  rabbitmq:
    host: 192.168.200.128
```

修改pom.xml项目配置文件，添加如下依赖
```
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

1.创建Rabbit管理器
  ```
  RabbitAdmin rabbitAdmin = new RabbitAdmin(rabbitTemplate.getConnectionFactory());
  ```
  
2.声明direct交换机处理新增消息

```
    DirectExchange exchange = new DirectExchange("article_subscribe");
    rabbitAdmin.declareExchange(exchange);
```

3.创建队列，每个用户都有自己的队列，通过用户id区分

```
    Queue queue = new Queue("article_subscribe_" + userId, true);
```

4.声明交换机和队列的绑定关系，确保队列只能收到对应消息，通过路由键和队列绑定到交换机上，例如

`
 队列 Q1 + 路由键key1 绑定到 交换机 exchange1 上
 这时如果携带路由键key1 的消息发送到交换机exchange1时，Q1就能收到，如果携带其他路由键则不能收到
`

```
//queue队列
//exchange交换机
//authorId是路由键
  Binding binding = BindingBuilder.bind(queue).to(exchange).with(authorId);
```

5.如果订阅，新增绑定关系。如果取消订阅，则需要删除绑定关系

```

 //删除绑定的队列,删除绑定关系
        rabbitAdmin.removeBinding(binding);
        
          //声明队列和绑定队列
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(binding);
```

## 2.发送消息


```
//article_subscribe 交换机
//authorId 路由键
//id 消息内容
 rabbitTemplate.convertAndSend("article_subscribe", authorId, id);
```

## 3.IO与NIO

`
类比餐厅。
服务员就是线程。
io类似每桌都需要一个服务员专门为一桌服务。
nio类似只有一个服务员，定时到每桌询问是否需要服务
`


## 4.Netty

pom依赖
```
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-all</artifactId>
    <version>4.1.5.Final</version>
</dependency>
```

demo/client

```
public class NettyClient {
    public static void main(String[] args) throws InterruptedException {


        // 不接受新的连接，并且是在父通道类完成一些操作，一般用于客户端的。
        Bootstrap bootstrap = new Bootstrap();

        // EventLoopGroup包含有多个EventLoop的实例，用来管理event Loop的组件
        NioEventLoopGroup group = new NioEventLoopGroup();

        //客户端执行
        bootstrap.group(group)
                // Channel对网络套接字的I/O操作，
                // 例如读、写、连接、绑定等操作进行适配和封装的组件。
                .channel(NioSocketChannel.class)
                // 用于对刚创建的channel进行初始化，
                // 将ChannelHandler添加到channel的ChannelPipeline处理链路中。
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        // 组件从流水线头部进入，流水线上的工人按顺序对组件进行加工
                        // 流水线相当于ChannelPipeline
                        // 流水线工人相当于ChannelHandler
                        ch.pipeline().addLast(new StringEncoder());
                    }
                });

        //客户端连接服务端
        Channel channel = bootstrap.connect("127.0.0.1", 8000).channel();

        while (true) {
            // 客户端使用writeAndFlush方法向服务端发送数据，返回的是ChannelFuture
            // 与jdk中线程的Future接口类似，即实现并行处理的效果
            // 可以在操作执行成功或失败时自动触发监听器中的事件处理方法。
            ChannelFuture future = channel.writeAndFlush("测试数据");
            Thread.sleep(2000);
        }
    }
}
```

demo/server
使用两个NioEventLoopGroup性能更高

```
public class NettyServer {

    public static void main(String[] args) {

        //创建bossGroup和workGroup
        //1.创建2个线程组bossGroup和workGroup
        //2.bossGroup只处理连接请求，workGroup客户端业务处理
        //3.两个都是无限循环
        /*
         * bossGroup和workGroup的子线程（NioEventLoop）数量
         * 默认是cpu核*2
         * */
        // 用于接受客户端的连接以及为已接受的连接创建子通道，一般用于服务端。
        ServerBootstrap serverBootstrap = new ServerBootstrap();

        // EventLoopGroup包含有多个EventLoop的实例，用来管理event Loop的组件
        // 接受新连接线程
        NioEventLoopGroup boos = new NioEventLoopGroup();
        // 读取数据的线程
        NioEventLoopGroup worker = new NioEventLoopGroup();

        //服务端执行
        serverBootstrap
                .group(boos, worker)
                // Channel对网络套接字的I/O操作，
                // 例如读、写、连接、绑定等操作进行适配和封装的组件。
                .channel(NioServerSocketChannel.class)
                // ChannelInitializer用于对刚创建的channel进行初始化
                // 将ChannelHandler添加到channel的ChannelPipeline处理链路中。
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    protected void initChannel(NioSocketChannel ch) {
                        // 组件从流水线头部进入，流水线上的工人按顺序对组件进行加工
                        // 流水线相当于ChannelPipeline
                        // 流水线工人相当于ChannelHandler
                        ch.pipeline().addLast(new StringDecoder());
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<String>() {
                            //这个工人有点麻烦，需要我们告诉他干啥事
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                                System.out.println(msg);
                            }
                        });
                    }
                })
                .bind(8000);

    }
}
```
 ## 5.Netty整合websocket
 
 
```
graph LR
页面websocket-->netty
netty-->页面websocket
netty-->rabbitmq
rabbitmq-->netty
```


1. 编写NettyServer，启动Netty服务。
2. 使用配置Bean创建Netty服务。编写NettyConfig。用于启动netty服务。
3. 编写和WebSocket进行通讯处理类MyWebSocketHandler（业务逻辑），进行MQ和WebSocket的消息处理。
4. hander由netty启动的时候new出新对象，因此hander里面不能使用注解，从容器中获取对象实例
4. 使用配置Bean创建Rabbit监听器容器，使用监听器。编写RabbitConfig。
5. 编写Rabbit监听器SysNoticeListener，用来获取MQ消息并进行处理。
6. mq监听器里面的消息会自动消费。

### 5.1.NettyServer
NettyServer,其中MyWebSocketHandler 是业务处理类，同时获取rabbitmq的数据，推送到websocket

```
public class NettyServer {

    public void start(int port) {
        System.out.println("准备启动Netty。。。");
        ServerBootstrap serverBootstrap = new ServerBootstrap();

        //用来处理新连接的
        EventLoopGroup boos = new NioEventLoopGroup();
        //用来处理业务逻辑的，读写。。。
        EventLoopGroup worker = new NioEventLoopGroup();

        serverBootstrap.group(boos, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        //请求消息解码器
                        ch.pipeline().addLast(new HttpServerCodec());
                        // 将多个消息转换为单一的request或者response对象
                        ch.pipeline().addLast(new HttpObjectAggregator(65536));
                        //处理WebSocket的消息事件
                        ch.pipeline().addLast(new WebSocketServerProtocolHandler("/ws"));

                        //创建自己的webSocket处理器，就是用来编写业务逻辑的
                        MyWebSocketHandler myWebSocketHandler = new MyWebSocketHandler();
                        ch.pipeline().addLast(myWebSocketHandler);
                    }
                }).bind(port);
    }
}
```

### 5.2.NettyConfig

用于启动Netty服务,@Configuration和@Bean注解

```
@Configuration
public class NettyConfig {

    @Bean
    public NettyServer createNettyServer() {
        NettyServer nettyServer = new NettyServer();

        //启动Netty服务，使用新的线程启动
        new Thread(){
            @Override
            public void run() {
               nettyServer.start(1234);
            }
        }.start();

        return nettyServer;
    }
}
```

### 5.3.MyWebSocketHandler
登录的时候从mq获取消息写到页面websocket中，在线的时候通过消息监听器主动从mq中获取消息，发送到页面websocket。

```
public class MyWebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static ObjectMapper MAPPER = new ObjectMapper();

    // 送Spring容器中获取消息监听器容器,处理订阅消息sysNotice
    SimpleMessageListenerContainer sysNoticeContainer = (SimpleMessageListenerContainer) ApplicationContextProvider.getApplicationContext()
            .getBean("sysNoticeContainer");

    //从Spring容器中获取RabbitTemplate
    RabbitTemplate rabbitTemplate = ApplicationContextProvider.getApplicationContext()
            .getBean(RabbitTemplate.class);

    //存放WebSocket连接Map，根据用户id存放
    public static ConcurrentHashMap<String, Channel> userChannelMap = new ConcurrentHashMap();

    //用户请求WebSocket服务端，执行的方法
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        //约定用户第一次请求携带的数据：{"userId":"1"}
        //获取用户请求数据并解析
        String json = msg.text();
        //解析json数据，获取用户id
        String userId = MAPPER.readTree(json).get("userId").asText();


        //第一次请求的时候，需要建立WebSocket连接
        Channel channel = userChannelMap.get(userId);
        if (channel == null) {
            //获取WebSocket的连接
            channel = ctx.channel();

            //把连接放到容器中
            userChannelMap.put(userId, channel);
        }

        //只用完成新消息的提醒即可，只需要获取消息的数量
        //获取RabbitMQ的消息内容，并发送给用户
        RabbitAdmin rabbitAdmin = new RabbitAdmin(rabbitTemplate);
        //拼接获取队列名称
        String queueName = "article_subscribe_" + userId;
        //获取Rabbit的Properties容器
        Properties queueProperties = rabbitAdmin.getQueueProperties(queueName);

        //获取消息数量
        int noticeCount = 0;
        //判断Properties是否不为空
        if (queueProperties != null) {
            // 如果不为空，获取消息的数量
            noticeCount = (int) queueProperties.get("QUEUE_MESSAGE_COUNT");
        }

        //封装返回的数据
        HashMap countMap = new HashMap();
        countMap.put("sysNoticeCount", noticeCount);
        Result result = new Result(true, StatusCode.OK, "查询成功", countMap);

        //把数据发送给用户
        channel.writeAndFlush(new TextWebSocketFrame(MAPPER.writeValueAsString(result)));

        //把消息从队列里面清空，否则MQ消息监听器会再次消费一次
        if (noticeCount > 0) {
            rabbitAdmin.purgeQueue(queueName, true);
        }

        //为用户的消息通知队列注册监听器，便于用户在线的时候，
        //一旦有消息，可以主动推送给用户，不需要用户请求服务器获取数据
        sysNoticeContainer.addQueueNames(queueName);
    }
}
```

### 5.4.RabbitConfig

消息监听器的容器

```
@Configuration
public class RabbitConfig {

    @Bean("sysNoticeContainer")
    public SimpleMessageListenerContainer create(ConnectionFactory connectionFactory) {
        SimpleMessageListenerContainer container =
                new SimpleMessageListenerContainer(connectionFactory);
        //使用Channel
        container.setExposeListenerChannel(true);
        //设置自己编写的监听器
        container.setMessageListener(new SysNoticeListener());

        return container;
    }
}
```

### 5.5.SysNoticeListener 

消息监听器，当有消息写入mq的时候触发onMessage
```
public class SysNoticeListener implements ChannelAwareMessageListener {

    private static ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        //获取用户id，可以通过队列名称获取
        String queueName = message.getMessageProperties().getConsumerQueue();
        String userId = queueName.substring(queueName.lastIndexOf("_") + 1);

        io.netty.channel.Channel wsChannel = MyWebSocketHandler.userChannelMap.get(userId);

        //判断用户是否在线
        if (wsChannel != null) {
            //如果连接不为空，表示用户在线
            //封装返回数据
            HashMap countMap = new HashMap();
            countMap.put("sysNoticeCount", 1);
            Result result = new Result(true, StatusCode.OK, "查询成功", countMap);

            // 把数据通过WebSocket连接主动推送用户
            wsChannel.writeAndFlush(new TextWebSocketFrame(MAPPER.writeValueAsString(result)));
        }
    }
}
```

### 5.6. http客户端写法

```

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
    <title>测试 notice 微服务与页面 websocket 交互</title>
</head>
<body>
<h1>
    websocket连接服务器获取mq消息测试
</h1>
<form onSubmit="return false;">
    <table><tr>
        <td><span>服务器地址：</span></td>
        <td><input type="text" id="serverUrl" value="ws://127.0.0.1:1234/ws" /></td>
      </tr>
      <tr>
        <td><input type="button" id="action" value="连接服务器" onClick="connect()" /></td>
        <td><input type="text" id="connStatus" value="未连接 ......" /></td>
    </tr></table>
    <br />
    <hr color="blue" />
    <div>
        <div style="width: 50%;float:left;">
            <div>
                <table><tr>
                    <td><h3>发送给服务端的消息</h3></td>
                    <td><input type="button" value="发送" onClick="send(this.form.message.value)" /></td>
                </tr></table>
            </div>
            <div><textarea type="text" name="message" style="width:500px;height:300px;">
{
    "userId":"1"
}
                </textarea></div>
        </div>
        <div style="width: 50%;float:left;">
            <div><table>
                <tr>
                    <td><h3>服务端返回的应答消息</h3></td>
                </tr>
            </table></div>
            <div><textarea id="responseText" name="responseText" style="width: 500px;height: 300px;" onfocus="this.scrollTop = this.scrollHeight ">
这里显示服务器推送的信息
            </textarea></div>
        </div>
    </div>

</form>

<script type="text/javascript">
    var socket;
    var connStatus = document.getElementById('connStatus');;
    var respText = document.getElementById('responseText');
    var actionBtn = document.getElementById('action');
    var sysCount = 0;
    var userCount = 0;

    function connect() {
        connStatus.value = "正在连接 ......";

        if(!window.WebSocket){
            window.WebSocket = window.MozWebSocket;
        }
        if(window.WebSocket){

            socket = new WebSocket("ws://127.0.0.1:1234/ws");

            socket.onmessage = function(event){
                respText.scrollTop = respText.scrollHeight;
                respText.value += "\r\n" + event.data;
                var sysData = JSON.parse(event.data).data.sysNoticeCount;
                if(sysData){
                    sysCount = sysCount + parseInt(sysData)
                }
                var userData = JSON.parse(event.data).data.userNoticeCount;
                if(userData){
                    userCount = userCount + parseInt(userData)
                }
                respText.value += "\r\n现在有" + sysCount + "条订阅新消息";
                respText.value += "\r\n现在有" + userCount + "条点赞新消息";
                respText.scrollTop = respText.scrollHeight;
            };
            socket.onopen = function(event){
                respText.value += "\r\nWebSocket 已连接";
                respText.scrollTop = respText.scrollHeight;

                connStatus.value = "已连接 O(∩_∩)O";

                actionBtn.value = "断开服务器";
                actionBtn.onclick =function(){
                    disconnect();
                };

            };
            socket.onclose = function(event){
                respText.value += "\r\n" + "WebSocket 已关闭";
                respText.scrollTop = respText.scrollHeight;

                connStatus.value = "已断开";

                actionBtn.value = "连接服务器";
                actionBtn.onclick = function() {
                    connect();
                };
            };

        } else {
            //alert("您的浏览器不支持WebSocket协议！");
            connStatus.value = "您的浏览器不支持WebSocket协议！";
        }
    }

    function disconnect() {
        socket.close();
    }

    function send(message){
        if(!window.WebSocket){return;}
        if(socket.readyState == WebSocket.OPEN){
            socket.send(message);
        }else{
            alert("WebSocket 连接没有建立成功！");
        }
    }
</script>
</body>
</html>
```
