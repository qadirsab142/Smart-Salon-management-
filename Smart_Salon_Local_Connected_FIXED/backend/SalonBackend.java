package backend;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class SalonBackend {
    static final int PORT = Integer.getInteger("ss.port", 8080);
    static final Path ROOT = Paths.get(System.getProperty("user.dir"));
    static final Path FRONTEND = ROOT.resolve("frontend").resolve("index.html");
    static final Path ADMIN_FRONTEND = ROOT.resolve("frontend").resolve("admin.html");
    static final Path DATA_DIR = ROOT.resolve("data");
    static final Path STORE_FILE = DATA_DIR.resolve("salon-store.bin");
    static Store store;
    static final Map<String,String> sessions = new ConcurrentHashMap<>();
    static final SecureRandom random = new SecureRandom();

    public static void main(String[] args) throws Exception {
        Files.createDirectories(DATA_DIR);
        store = loadStore();
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", SalonBackend::handleRoot);
        server.createContext("/api/health", SalonBackend::health);
        server.createContext("/api/auth", SalonBackend::auth);
        server.createContext("/api/stylists", SalonBackend::stylists);
        server.createContext("/api/appointments", SalonBackend::appointments);
        server.createContext("/api/profile", SalonBackend::profile);
        server.createContext("/api/admin/login", SalonBackend::adminLogin);
        server.createContext("/api/admin/stats", SalonBackend::adminStats);
        server.createContext("/api/admin/data", SalonBackend::adminData);
        server.createContext("/api/admin/appointment", SalonBackend::adminAppointment);
        server.createContext("/api/admin/stylist", SalonBackend::adminStylist);
        server.createContext("/api/admin/user", SalonBackend::adminUser);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("SMART SALON backend running: http://localhost:" + PORT);
        System.out.println("Admin login: admin / Admin@123");
    }

    static synchronized Store loadStore() {
        if (Files.exists(STORE_FILE)) try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(STORE_FILE))) {
            return (Store) in.readObject();
        } catch (Exception ignored) {}
        Store s = new Store();
        s.users.add(new User("U-1001","Alex Morgan","alex@example.com","+92 300 0000000",hash("User@123"),"USER"));
        s.admins.add(new Admin("A-001","admin",hash("Admin@123"),"Salon Owner"));
        s.stylists.add(new Stylist("ahmed","Ahmed Khan","Master Barber",8,4.9,true,"Classic Haircut,Hair + Beard,Premium Grooming"));
        s.stylists.add(new Stylist("hamza","Hamza Ali","Hair & Beard Specialist",6,4.8,true,"Hair + Beard,Beard Styling"));
        s.stylists.add(new Stylist("daniyal","Daniyal Shah","Grooming Expert",7,4.9,true,"Beard Styling,Premium Grooming,Hair + Beard"));
        s.stylists.add(new Stylist("usman","Usman Malik","Styling Specialist",5,4.8,true,"Classic Haircut,Premium Grooming"));
        s.appointments.add(new Appointment("SS-2401","U-1001","Alex Morgan","Hair + Beard","Ahmed Khan","18 Sep 2026","3:30 PM",4200,"Confirmed",now()));
        s.transactions.add(new Transaction("TX-1001","SS-2401",4200,"Confirmed",now()));
        save(s); return s;
    }
    static synchronized void save(Store s) { try { Files.createDirectories(DATA_DIR); try(ObjectOutputStream out=new ObjectOutputStream(Files.newOutputStream(STORE_FILE))){out.writeObject(s);} } catch(Exception e){e.printStackTrace();} }
    static String now(){return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}
    static String hash(String p){try{byte[] salt="SmartSalonV1".getBytes(StandardCharsets.UTF_8); PBEKeySpec sp=new PBEKeySpec(p.toCharArray(),salt,120000,256); return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(sp).getEncoded());}catch(Exception e){throw new RuntimeException(e);}}
    static boolean check(String p,String h){return MessageDigest.isEqual(hash(p).getBytes(StandardCharsets.UTF_8),h.getBytes(StandardCharsets.UTF_8));}

   static void handleRoot(HttpExchange ex) throws IOException {
    if (!ex.getRequestMethod().equals("GET")) {
        send(ex,405,"text/plain","Method Not Allowed");
        return;
    }

    String path = ex.getRequestURI().getPath();

    // PUBLIC WEBSITE
    if (path.equals("/") || path.equals("/index.html")) {
        String html = new String(
            Files.readAllBytes(FRONTEND),
            StandardCharsets.UTF_8
        );

        html += "\n<script>" + bridgeScript() + "</script>\n";

        send(
            ex,
            200,
            "text/html; charset=UTF-8",
            html
        );
        return;
    }

    // ADMIN WEBSITE
    if (path.equals("/admin") ||
        path.equals("/admin/") ||
        path.equals("/admin.html")) {

        String html = new String(
            Files.readAllBytes(ADMIN_FRONTEND),
            StandardCharsets.UTF_8
        );

        send(
            ex,
            200,
            "text/html; charset=UTF-8",
            html
        );
        return;
    }

    send(ex,404,"text/plain","Not Found");
}
    static String bridgeScript(){
        return """
(() => {
 const API=window.location.origin+'/api';
 let token=localStorage.getItem('ss_token')||'';
 async function post(path,data){const r=await fetch(API+path,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(data)});return r.json()}
 async function get(path){const r=await fetch(API+path,{headers:{'Authorization':'Bearer '+token}});return r.json()}
 window.signIn=async function(e){e.preventDefault();const email=document.getElementById('signin-email').value,password=document.getElementById('signin-password').value;const d=await post('/auth',{action:'login',email,password});if(!d.ok){document.getElementById('auth-message').textContent=d.message||'Login failed';return;}token=d.token;localStorage.setItem('ss_token',token);signedIn=true;user=d.user;closeModal('auth-modal');if(pendingBooking){pendingBooking=false;openBooking()}else showPage('dashboard');renderDashboard()}
 window.createAccount=async function(e){e.preventDefault();const p=document.getElementById('signup-password').value,c=document.getElementById('signup-confirm').value;if(p!==c){document.getElementById('auth-message').textContent='Passwords do not match.';return;}const d=await post('/auth',{action:'signup',name:document.getElementById('signup-name').value,email:document.getElementById('signup-email').value,phone:document.getElementById('signup-phone').value,password:p});if(!d.ok){document.getElementById('auth-message').textContent=d.message||'Signup failed';return;}token=d.token;localStorage.setItem('ss_token',token);signedIn=true;user=d.user;closeModal('auth-modal');if(pendingBooking){pendingBooking=false;openBooking()}else showPage('dashboard');renderDashboard()}
 window.signOut=async function(){if(token) await post('/auth',{action:'logout',token});token='';localStorage.removeItem('ss_token');signedIn=false;showPage('home')}
 const oldConfirm=window.confirmBooking; window.confirmBooking=async function(){const d=await post('/appointments',{token,service:booking.service.name,date:booking.date,time:booking.time,stylist:booking.stylist});if(!d.ok){document.getElementById('booking-error').textContent=d.message||'Booking failed';return;}appointments.unshift(d.appointment);document.getElementById('booking-form').classList.add('hide');document.getElementById('booking-success').classList.remove('hide');document.getElementById('booking-confirmation-details').innerHTML='<strong>Booking ID</strong>: '+d.appointment.id+'<br><strong>Service</strong>: '+d.appointment.service+'<br><strong>Stylist</strong>: '+d.appointment.stylist+'<br><strong>Date</strong>: '+d.appointment.date+'<br><strong>Time</strong>: '+d.appointment.time; if(window.lucide) lucide.createIcons();}
 window.renderDashboard=async function(){if(!signedIn)return;const d=await get('/appointments/mine');if(d.ok)appointments=d.appointments;const p=await get('/profile');if(p.ok)user=p.user;document.getElementById('dashboard-greeting').textContent='Good morning, '+user.name.split(' ')[0]+'.';const active=appointments.filter(a=>a.status==='Confirmed'||a.status==='Pending');document.getElementById('metric-upcoming-value').textContent=active.length;document.getElementById('upcoming-card').innerHTML=active[0]?appointmentCard(active[0]):'<p class="py-8 text-stone-500">No upcoming appointments. Your next great look is one booking away.</p>';document.getElementById('appointments-list').innerHTML=appointments.map(appointmentCard).join('');renderHistory();document.getElementById('profile-name-input').value=user.name;document.getElementById('profile-email-input').value=user.email;document.getElementById('profile-phone-input').value=user.phone;}
 window.cancelAppointment=async function(id){const d=await post('/appointments',{token,action:'cancel',id});if(d.ok)renderDashboard()}
 window.reschedule=async function(id){const d=await post('/appointments',{token,action:'reschedule',id});if(d.ok)renderDashboard()}
 window.saveProfile=async function(e){e.preventDefault();const d=await post('/profile',{token,name:document.getElementById('profile-name-input').value,email:document.getElementById('profile-email-input').value,phone:document.getElementById('profile-phone-input').value});document.getElementById('profile-message').textContent=d.ok?'Profile saved successfully.':(d.message||'Unable to save');if(d.ok){user=d.user;renderDashboard()}}
 const oldShow=window.showPage; window.showPage=function(page){if(page==='dashboard'&&!signedIn){pendingBooking=false;openAuth('signin');return;}oldShow(page);if(page==='dashboard')setTimeout(renderDashboard,50)};
 window.addEventListener('load',async()=>{if(token){const d=await get('/auth/session');if(d.ok){signedIn=true;user=d.user;renderDashboard()}}});
})();
""";
    }
    static void health(HttpExchange ex)throws IOException{sendJson(ex,200,"{\"ok\":true,\"service\":\"Smart Salon Java Backend\",\"time\":\""+esc(now())+"\"}");}
    static void auth(HttpExchange ex)throws IOException{Map<String,String> p=form(ex);String a=p.getOrDefault("action","");if(a.equals("login")){for(User u:store.users)if(u.email.equalsIgnoreCase(p.getOrDefault("email",""))&&check(p.getOrDefault("password",""),u.password)){String t=token("USER:"+u.id);sendJson(ex,200,"{\"ok\":true,\"token\":\""+t+"\",\"user\":"+ujson(u)+"}");return;}sendJson(ex,401,"{\"ok\":false,\"message\":\"Invalid email or password.\"}");return;}if(a.equals("signup")){String email=p.getOrDefault("email","");for(User u:store.users)if(u.email.equalsIgnoreCase(email)){sendJson(ex,409,"{\"ok\":false,\"message\":\"Email already registered.\"}");return;}User u=new User("U-"+(1000+store.users.size()+1),p.get("name"),email,p.get("phone"),hash(p.get("password")),"USER");store.users.add(u);save(store);String t=token("USER:"+u.id);sendJson(ex,200,"{\"ok\":true,\"token\":\""+t+"\",\"user\":"+ujson(u)+"}");return;}if(a.equals("logout")){sessions.remove(p.get("token"));sendJson(ex,200,"{\"ok\":true}");return;}if(a.equals("session")){String uid=userFrom(ex);User u=findUser(uid);if(u!=null){sendJson(ex,200,"{\"ok\":true,\"user\":"+ujson(u)+"}");return;}}sendJson(ex,401,"{\"ok\":false,\"message\":\"Unauthorized\"}");}
    static void stylists(HttpExchange ex)throws IOException{StringBuilder b=new StringBuilder("{\"ok\":true,\"stylists\":[");for(int i=0;i<store.stylists.size();i++){if(i>0)b.append(',');b.append(sjson(store.stylists.get(i)));}b.append("]}");sendJson(ex,200,b.toString());}
    static void appointments(HttpExchange ex)throws IOException{Map<String,String> p=form(ex);String uid=userFromToken(p.get("token"));if(uid==null){sendJson(ex,401,"{\"ok\":false,\"message\":\"Please sign in.\"}");return;}if("cancel".equals(p.get("action"))||"reschedule".equals(p.get("action"))){for(Appointment a:store.appointments)if(a.id.equals(p.get("id"))&&a.userId.equals(uid)){if("cancel".equals(p.get("action")))a.status="Cancelled";else{a.time=a.time.equals("3:30 PM")?"5:00 PM":"3:30 PM";a.status="Pending";}save(store);sendJson(ex,200,"{\"ok\":true}");return;}sendJson(ex,404,"{\"ok\":false}");return;}if("GET".equals(ex.getRequestMethod())){List<Appointment> mine=new ArrayList<>();for(Appointment a:store.appointments)if(a.userId.equals(uid))mine.add(a);sendAppointments(ex,mine);return;}String service=p.get("service"),date=p.get("date"),time=p.get("time"),sid=p.getOrDefault("stylist","recommend");Stylist chosen=null;if(!"recommend".equals(sid))for(Stylist s:store.stylists)if(s.id.equals(sid))chosen=s;if(chosen==null){chosen=store.stylists.stream().filter(s->s.active).max(Comparator.comparingDouble(s->s.rating)).orElse(store.stylists.get(0));}int price=price(service);String id="SS-"+(2000+store.appointments.size()+1);User u=findUser(uid);Appointment a=new Appointment(id,uid,u.name,service,date,time,chosen.name,price,"Confirmed",now());store.appointments.add(0,a);store.transactions.add(new Transaction("TX-"+(1000+store.transactions.size()+1),id,price,"Confirmed",now()));save(store);sendJson(ex,200,"{\"ok\":true,\"appointment\":"+ajson(a)+"}");}
    static void profile(HttpExchange ex)throws IOException{Map<String,String> p=form(ex);String uid=userFromToken(p.get("token"));if(uid==null)uid=userFrom(ex);User u=findUser(uid);if(u==null){sendJson(ex,401,"{\"ok\":false}");return;}if("POST".equals(ex.getRequestMethod())){u.name=p.getOrDefault("name",u.name);u.email=p.getOrDefault("email",u.email);u.phone=p.getOrDefault("phone",u.phone);save(store);}sendJson(ex,200,"{\"ok\":true,\"user\":"+ujson(u)+"}");}
   static void adminLogin(HttpExchange ex)throws IOException {

    Map<String,String> p = form(ex);

    String email = p.getOrDefault("email","").trim();
    String password = p.getOrDefault("password","");

    // Smart Salon owner admin account
    String ADMIN_EMAIL = "admin@smartsalon.com";

    if (ADMIN_EMAIL.equalsIgnoreCase(email)) {

        for (Admin a : store.admins) {

            if (
                a.username.equals("admin") &&
                check(password,a.password)
            ) {

                String t = token("ADMIN:" + a.id);

                sendJson(
                    ex,
                    200,
                    "{\"ok\":true," +
                    "\"token\":\"" + t + "\"," +
                    "\"admin\":{" +
                    "\"name\":\"" + esc(a.name) + "\"," +
                    "\"email\":\"" + ADMIN_EMAIL + "\"," +
                    "\"role\":\"" + esc(a.role) + "\"" +
                    "}}"
                );

                return;
            }
        }
    }

    sendJson(
        ex,
        401,
        "{\"ok\":false,\"message\":\"Invalid admin email or password.\"}"
    );
}
    static boolean isAdmin(HttpExchange ex){String h=ex.getRequestHeaders().getFirst("Authorization");return h!=null&&sessions.getOrDefault(h.replace("Bearer ",""),"").startsWith("ADMIN:");}
    static void adminStats(HttpExchange ex)throws IOException{if(!isAdmin(ex)){sendJson(ex,401,"{\"ok\":false}");return;}long confirmed=store.appointments.stream().filter(a->a.status.equals("Confirmed")||a.status.equals("Pending")).count(),completed=store.appointments.stream().filter(a->a.status.equals("Completed")).count(),cancelled=store.appointments.stream().filter(a->a.status.equals("Cancelled")).count();int revenue=store.transactions.stream().filter(t->t.status.equals("Confirmed")).mapToInt(t->t.amount).sum();sendJson(ex,200,"{\"ok\":true,\"users\":"+store.users.size()+",\"appointments\":"+store.appointments.size()+",\"active\":"+confirmed+",\"completed\":"+completed+",\"cancelled\":"+cancelled+",\"stylists\":"+store.stylists.stream().filter(s->s.active).count()+",\"revenue\":"+revenue+"}");}
    static void adminData(HttpExchange ex)throws IOException{if(!isAdmin(ex)){sendJson(ex,401,"{\"ok\":false}");return;}StringBuilder b=new StringBuilder("{\"ok\":true,\"appointments\":[");for(int i=0;i<store.appointments.size();i++){if(i>0)b.append(',');b.append(ajson(store.appointments.get(i)));}b.append("],\"users\":[");for(int i=0;i<store.users.size();i++){if(i>0)b.append(',');b.append(ujson(store.users.get(i)));}b.append("],\"stylists\":[");for(int i=0;i<store.stylists.size();i++){if(i>0)b.append(',');b.append(sjson(store.stylists.get(i)));}b.append("]}");sendJson(ex,200,b.toString());}
   static void adminAppointment(HttpExchange ex)throws IOException {

    if (!isAdmin(ex)) {
        sendJson(ex,401,"{\"ok\":false,\"message\":\"Unauthorized\"}");
        return;
    }

    Map<String,String> p = form(ex);

    for (Appointment a : store.appointments) {

        if (a.id.equals(p.get("id"))) {

            if (p.containsKey("status")) {
                a.status = p.get("status");
            }

            if (p.containsKey("stylist")) {
                a.stylist = p.get("stylist");
            }

            if (p.containsKey("date")) {
                a.date = p.get("date");
            }

            if (p.containsKey("time")) {
                a.time = p.get("time");
            }

            if (p.containsKey("service")) {
                a.service = p.get("service");
                a.price = price(a.service);
            }

            save(store);

            sendJson(
                ex,
                200,
                "{\"ok\":true}"
            );

            return;
        }
    }

    sendJson(
        ex,
        404,
        "{\"ok\":false,\"message\":\"Appointment not found.\"}"
    );
}
   static void adminStylist(HttpExchange ex)throws IOException {

    if (!isAdmin(ex)) {
        sendJson(ex,401,"{\"ok\":false,\"message\":\"Unauthorized\"}");
        return;
    }

    Map<String,String> p = form(ex);

    for (Stylist s : store.stylists) {

        if (s.id.equals(p.get("id"))) {

            if (p.containsKey("name")) {
                s.name = p.get("name");
            }

            if (p.containsKey("role")) {
                s.role = p.get("role");
            }

            if (p.containsKey("experience")) {
                try {
                    s.experience = Integer.parseInt(
                        p.get("experience")
                    );
                } catch(Exception ignored) {}
            }

            if (p.containsKey("rating")) {
                try {
                    s.rating = Double.parseDouble(
                        p.get("rating")
                    );
                } catch(Exception ignored) {}
            }

            if (p.containsKey("active")) {
                s.active = Boolean.parseBoolean(
                    p.get("active")
                );
            }

            if (p.containsKey("services")) {
                s.services = p.get("services");
            }

            save(store);

            sendJson(
                ex,
                200,
                "{\"ok\":true}"
            );

            return;
        }
    }

    sendJson(
        ex,
        404,
        "{\"ok\":false,\"message\":\"Stylist not found.\"}"
    );
}
   static void adminUser(HttpExchange ex)throws IOException {

    if (!isAdmin(ex)) {
        sendJson(ex,401,"{\"ok\":false,\"message\":\"Unauthorized\"}");
        return;
    }

    Map<String,String> p = form(ex);

    for (User u : store.users) {

        if (u.id.equals(p.get("id"))) {

            if (p.containsKey("name")) {
                u.name = p.get("name");
            }

            if (p.containsKey("email")) {
                u.email = p.get("email");
            }

            if (p.containsKey("phone")) {
                u.phone = p.get("phone");
            }

            save(store);

            sendJson(
                ex,
                200,
                "{\"ok\":true}"
            );

            return;
        }
    }

    sendJson(
        ex,
        404,
        "{\"ok\":false,\"message\":\"Customer not found.\"}"
    );
}

    static int price(String s){return switch(s){case "Classic Haircut"->2800;case "Hair + Beard"->4200;case "Beard Styling"->1800;case "Premium Grooming"->6000;default->0;};}
    static String token(String v){String t=UUID.randomUUID().toString();sessions.put(t,v);return t;}
    static String userFrom(HttpExchange ex){String h=ex.getRequestHeaders().getFirst("Authorization");return h==null?null:userFromToken(h.replace("Bearer ",""));}
    static String userFromToken(String t){String v=sessions.get(t==null?"":t);return v!=null&&v.startsWith("USER:")?v.substring(5):null;}
    static User findUser(String id){for(User u:store.users)if(u.id.equals(id))return u;return null;}
    static Map<String,String> form(HttpExchange ex)throws IOException{String body=new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);Map<String,String> m=new HashMap<>();for(String x:body.split("&")){if(x.isEmpty())continue;String[] kv=x.split("=",2);m.put(URLDecoder.decode(kv[0],"UTF-8"),kv.length>1?URLDecoder.decode(kv[1],"UTF-8"):"");}return m;}
    static void sendAppointments(HttpExchange ex,List<Appointment> list)throws IOException{StringBuilder b=new StringBuilder("{\"ok\":true,\"appointments\":[");for(int i=0;i<list.size();i++){if(i>0)b.append(',');b.append(ajson(list.get(i)));}b.append("]}");sendJson(ex,200,b.toString());}
    static String ujson(User u){return "{\"id\":\""+esc(u.id)+"\",\"name\":\""+esc(u.name)+"\",\"email\":\""+esc(u.email)+"\",\"phone\":\""+esc(u.phone)+"\"}";}
    static String sjson(Stylist s){return "{\"id\":\""+esc(s.id)+"\",\"name\":\""+esc(s.name)+"\",\"role\":\""+esc(s.role)+"\",\"experience\":"+s.experience+",\"rating\":"+s.rating+",\"active\":"+s.active+",\"services\":\""+esc(s.services)+"\"}";}
    static String ajson(Appointment a){return "{\"id\":\""+esc(a.id)+"\",\"userId\":\""+esc(a.userId)+"\",\"customer\":\""+esc(a.customer)+"\",\"service\":\""+esc(a.service)+"\",\"stylist\":\""+esc(a.stylist)+"\",\"date\":\""+esc(a.date)+"\",\"time\":\""+esc(a.time)+"\",\"price\":"+a.price+",\"status\":\""+esc(a.status)+"\",\"createdAt\":\""+esc(a.createdAt)+"\"}";}
    static String esc(String s){if(s==null)return "";return s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n");}
    static void sendJson(HttpExchange ex,int code,String json)throws IOException{send(ex,code,"application/json; charset=UTF-8",json);}
    static void send(HttpExchange ex,int code,String type,String body)throws IOException{byte[] d=body.getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type",type);ex.getResponseHeaders().set("Access-Control-Allow-Origin","*");ex.getResponseHeaders().set("Access-Control-Allow-Headers","Content-Type,Authorization");ex.getResponseHeaders().set("Access-Control-Allow-Methods","GET,POST,OPTIONS");if(ex.getRequestMethod().equals("OPTIONS")){ex.sendResponseHeaders(204,-1);return;}ex.sendResponseHeaders(code,d.length);try(OutputStream o=ex.getResponseBody()){o.write(d);}}

    static class Store implements Serializable {List<User> users=new ArrayList<>();List<Admin> admins=new ArrayList<>();List<Stylist> stylists=new ArrayList<>();List<Appointment> appointments=new ArrayList<>();List<Transaction> transactions=new ArrayList<>();}
    static class User implements Serializable {String id,name,email,phone,password,role;User(String i,String n,String e,String p,String pw,String r){id=i;name=n;email=e;phone=p;password=pw;role=r;}}
    static class Admin implements Serializable {String id,username,password,name,role;Admin(String i,String u,String p,String r){id=i;username=u;password=p;name=u;role=r;}}
    static class Stylist implements Serializable {String id,name,role,services;int experience;double rating;boolean active;Stylist(String i,String n,String r,int e,double rt,boolean a,String sv){id=i;name=n;role=r;experience=e;rating=rt;active=a;services=sv;}}
    static class Appointment implements Serializable {String id,userId,customer,service,stylist,date,time,status,createdAt;int price;Appointment(String i,String u,String c,String s,String st,String d,String t,int p,String stat,String cr){id=i;userId=u;customer=c;service=s;stylist=st;date=d;time=t;price=p;status=stat;createdAt=cr;}}
    static class Transaction implements Serializable {String id,appointmentId,status,createdAt;int amount;Transaction(String i,String a,int n,String s,String c){id=i;appointmentId=a;amount=n;status=s;createdAt=c;}}
}
