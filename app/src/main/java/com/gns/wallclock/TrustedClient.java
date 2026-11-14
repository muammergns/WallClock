package com.gns.wallclock;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

//eski android sürümlerinin kök sertifika listesinde olmayan kökler (AFAD: GlobalSign Root R46, EMSC: ISRG Root X1) uygulamayla gelir.
//sistemin güvendiği kökler geçerliliğini korur, bu kökler onlara eklenir.
//android 4.1-4.4 TLS 1.1/1.2'yi destekler ama varsayılan olarak kapalı tutar, AFAD en az TLS 1.1 istediği için açılır.
public class TrustedClient {
    private static final int[] ROOTS = {R.raw.globalsign_root_r46, R.raw.isrg_root_x1};

    public static OkHttpClient create(Context context) {
        try {
            X509TrustManager trustManager = new CombinedTrustManager(systemTrustManager(null), systemTrustManager(bundledRoots(context)));
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, null);
            return new OkHttpClient.Builder()
                    .sslSocketFactory(new ModernTlsSocketFactory(sslContext.getSocketFactory()), trustManager)
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("TrustedClient: certificates not loaded", e);
        }
    }

    private static KeyStore bundledRoots(Context context) throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        for (int id : ROOTS) {
            try (InputStream in = context.getResources().openRawResource(id)) {
                keyStore.setCertificateEntry("root" + id, factory.generateCertificate(in));
            }
        }
        return keyStore;
    }

    private static X509TrustManager systemTrustManager(KeyStore keyStore) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore);
        for (TrustManager tm : factory.getTrustManagers())
            if (tm instanceof X509TrustManager) return (X509TrustManager) tm;
        throw new GeneralSecurityException("no X509TrustManager");
    }

    private static class CombinedTrustManager implements X509TrustManager {
        private final X509TrustManager system, bundled;

        CombinedTrustManager(X509TrustManager system, X509TrustManager bundled) {
            this.system = system;
            this.bundled = bundled;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            system.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                system.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                bundled.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> all = new ArrayList<>(Arrays.asList(system.getAcceptedIssuers()));
            all.addAll(Arrays.asList(bundled.getAcceptedIssuers()));
            return all.toArray(new X509Certificate[0]);
        }
    }

    private static class ModernTlsSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        ModernTlsSocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        private Socket enable(Socket socket) {
            if (socket instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) socket;
                List<String> protocols = new ArrayList<>(Arrays.asList(ssl.getEnabledProtocols()));
                for (String p : ssl.getSupportedProtocols())
                    if ((p.equals("TLSv1.1") || p.equals("TLSv1.2")) && !protocols.contains(p)) protocols.add(p);
                ssl.setEnabledProtocols(protocols.toArray(new String[0]));
            }
            return socket;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return enable(delegate.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return enable(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return enable(delegate.createSocket(address, port, localAddress, localPort));
        }
    }
}
