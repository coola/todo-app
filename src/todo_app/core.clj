(ns todo-app.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.util.response :refer [response redirect]]
            [hiccup.page :refer [html5]]
            [hiccup.form :as form]
            [clojure.java.io :as io]))

;; Persistent data store
(def data-file "data.edn")
(def users (atom {}))
(def todos (atom {})) ; {username {project-name [{:id :task :done}]}}

(defn html-layout [title & body]
  (html5
   [:head
    [:meta {:charset "UTF-8"}]
    [:title title]
    [:style "
      body { font-family: Arial, sans-serif; max-width: 800px; margin: 50px auto; padding: 20px; }
      .login-form, .todo-container { background: #f5f5f5; padding: 20px; border-radius: 8px; }
      input[type=text], input[type=password] { padding: 8px; margin: 5px 0; width: 100%; box-sizing: border-box; }
      button { padding: 10px 20px; background: #007bff; color: white; border: none; cursor: pointer; border-radius: 4px; margin: 5px 0; }
      button:hover { background: #0056b3; }
      .project { margin: 20px 0; padding: 15px; background: white; border-radius: 5px; }
      .todo-item { padding: 10px; margin: 5px 0; background: #e9ecef; border-radius: 3px; display: flex; align-items: center; }
      .todo-item.done { text-decoration: line-through; opacity: 0.6; }
      .logout { float: right; }
      h2 { color: #333; }
      .add-form { margin: 10px 0; }
    "]]
   [:body body]))

(defn login-page [error]
  (html-layout "Login"
    [:div.login-form
     [:h2 "Todo App Login"]
     (when error [:p {:style "color: red;"} error])
     (form/form-to [:post "/login"]
       [:div
        [:label "Username:"]
        (form/text-field {:required true} "username")]
       [:div
        [:label "Password:"]
        (form/password-field {:required true} "password")]
       [:div
        (form/submit-button "Login")])]))

(defn todo-page [username]
  (let [user-todos (get @todos username {})]
    (html-layout
     "My Todos"
     [:div.todo-container
      [:h1 "Welcome, " username "!"]
      [:a.logout {:href "/logout"} [:button "Logout"]]
      [:h2 "Add New Project"]
      (form/form-to [:post "/add-project"]
                    [:div.add-form
                     (form/text-field {:placeholder "Project name" :required true} "project")
                     (form/submit-button "Create Project")])
      [:h2 "Projects"]
      (if (empty? user-todos)
        [:p "No projects yet. Create one above!"]
        (for [[project tasks] user-todos]
          [:div.project
           [:h3 project]
           (form/form-to [:post "/delete-project"]
                         (form/hidden-field "project" project)
                         (form/submit-button "Delete Project"))
           (form/form-to [:post "/add-task"]
                         [:div.add-form
                          (form/hidden-field "project" project)
                          (form/text-field {:placeholder "New task" :required true} "task")
                          (form/submit-button "Add Task")])
           (if (empty? tasks)
             [:p "No tasks in this project."]
             [:div
              (for [task tasks]
                [:div.todo-item {:class (when (:done task) "done")}
                 (form/form-to [:post "/toggle-task"]
                               (form/hidden-field "project" project)
                               (form/hidden-field "id" (:id task))
                               (form/submit-button (if (:done task) "✓" "○")))
                 [:span {:style "margin-left: 10px; flex-grow: 1;"} (:task task)]
                 (form/form-to [:post "/delete-task"]
                               (form/hidden-field "project" project)
                    (form/hidden-field "id" (:id task))
                    (form/submit-button "Delete"))])])]))])))

(defn save-data []
  (spit data-file (pr-str {:users @users :todos @todos})))

(defn load-data []
  (if (.exists (io/file data-file))
    (let [data (read-string (slurp data-file))]
      (reset! users (:users data))
      (reset! todos (:todos data)))
    ;; If file doesn't exist, initialize with default admin user and save it.
    (do
      (reset! users {"admin" "password123"})
      (save-data))))

(defn handle-login [username password]
  (if (= (get @users username) password)
    (-> (redirect "/")
        (assoc :session {:username username}))
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (login-page "Invalid username or password")}))

(defn handle-add-project [username project]
  (when-not (empty? project)
    (swap! todos assoc-in [username project] []))
  (save-data)
  (redirect "/"))

(defn handle-add-task [username project task]
  (when-not (or (empty? project) (empty? task))
    (let [new-task {:id (str (random-uuid))
                    :task task
                    :done false}]
      (swap! todos update-in [username project] (fnil conj []) new-task)))
  (save-data)
  (redirect "/"))

(defn handle-toggle-task [username project task-id]
  (swap! todos update-in [username project]
         (fn [tasks]
           (mapv #(if (= (:id %) task-id)
                    (update % :done not)
                    %)
                 tasks)))
  (save-data)
  (redirect "/"))

(defn handle-delete-task [username project task-id]
  (swap! todos update-in [username project]
         (fn [tasks]
           (filterv #(not= (:id %) task-id) tasks)))
  (save-data)
  (redirect "/"))

(defn handle-delete-project [username project]
  (swap! todos update-in [username] dissoc project)
  (save-data)
  (redirect "/"))

(defn handler [request]
  (let [uri (:uri request)
        method (:request-method request)
        session (:session request)
        username (:username session)
        params (:params request)]
    (cond
      ;; Logout
      (= uri "/logout")
      (-> (redirect "/login")
          (assoc :session {}))
      
      ;; Login page
      (and (= uri "/login") (= method :get))
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (login-page nil)}
      
      ;; Login POST
      (and (= uri "/login") (= method :post))
      (handle-login (get params "username") (get params "password"))
      
      ;; Protected routes (require login)
      (nil? username)
      (redirect "/login")
      
      ;; Home page
      (= uri "/")
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (todo-page username)}
      
      ;; Add project
      (and (= uri "/add-project") (= method :post))
      (handle-add-project username (get params "project"))
      
      ;; Add task
      (and (= uri "/add-task") (= method :post))
      (handle-add-task username (get params "project") (get params "task"))
      
      ;; Toggle task
      (and (= uri "/toggle-task") (= method :post))
      (handle-toggle-task username (get params "project") (get params "id"))
      
      ;; Delete task
      (and (= uri "/delete-task") (= method :post))
      (handle-delete-task username (get params "project") (get params "id"))

      ;; Delete project
      (and (= uri "/delete-project") (= method :post))
      (handle-delete-project username (get params "project"))
      
      ;; 404
      :else
      {:status 404
       :headers {"Content-Type" "text/html"}
       :body "Not found"})))

(def app
  (-> handler
      wrap-params
      wrap-session))

(defn -main []
  (load-data)
  (println "Starting server on http://localhost:3000")
  (println "Login with username: admin, password: password123")
  (run-jetty app {:port 3000 :join? false}))

;; For REPL development
(comment
  (-main))
