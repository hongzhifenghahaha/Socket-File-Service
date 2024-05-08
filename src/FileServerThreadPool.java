import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 该类是实现了线程池的文件服务器
 *
 * @author Sun Shuo
 * @version 1.2.0
 * @see Service
 */
public class FileServerThreadPool {
    // 定义tcp端口，udp端口 和 单个处理器线程池工作线程数目
    private final int TCP_PORT = 2021;
    private final int UDP_PORT = 2020;
    private final int POOL_SIZE = 4;

    // 定义根目录
    private static String root = null;

    ServerSocket serverSocket;
    DatagramSocket udpSocket;
    // 定义线程池
    ExecutorService executorService;

    /**
     * 构造方法，初始化 serverSocket，udpSocket 和 executorService
     */
    public FileServerThreadPool() throws IOException {
        // 初始化tcp socket 和 udp socket
        serverSocket = new ServerSocket(TCP_PORT);
        udpSocket = new DatagramSocket(UDP_PORT);

        System.out.println("服务器线程数： " + Runtime.getRuntime().availableProcessors() * POOL_SIZE);
        // 初始化线程池
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * POOL_SIZE);
        System.out.println("服务器启动。");

    }

    /**
     * main方法，从参数中获取根目录的路径，并决定是否启动服务
     * @param args 根目录路径参数
     */
    public static void main(String[] args) throws IOException {
        // 参数中没有根目录则报错
        if (!(args.length > 0)) {
            System.out.println("错误：请输入root地址作为参数");
        }else{
            // 参数中有根目录则启动服务
            root = args[0];
            File file = new File(root);
            // 判断根目录是否合法，合法则启动服务器，并调用service方法
            if (file.exists() && file.isDirectory()){
                new FileServerThreadPool().service();
            } else {
                // 不合法则提示用户
                System.out.println("root is invalid!");
            }
        }
    }

    /**
     * 启动服务， serverSocket 监听TCP的2021端口并等待连接。当一个客户端连接到来后会新建一个线程提供服务
     */
    public void service(){

        // 监听tcp端口，等待建立连接
        while(true){
            try{
                // 等待用户连接
                Socket tcpSocket = serverSocket.accept();
                // 把执行交给线程池来维护
                executorService.execute(new Service(tcpSocket, udpSocket, root));
            } catch (IOException e){
                e.printStackTrace();
            }
            try {
                // 防止过度占用cpu
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
