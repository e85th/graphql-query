(ns e85th.graphql-query.core
  "Provides functions to substitute values in graphql query templates."
  (:require #?@(:clj [[clojure.edn        :as edn]
                      [clojure.java.io    :as io]]
               :cljs [[cljs.reader       :as edn]])
            [clojure.spec.alpha :as s]
            [clojure.string     :as str])
  #?(:clj
     (:import [clojure.lang IPersistentMap Keyword IPersistentCollection]
              [java.util UUID])))

;;----------------------------------------------------------------------
;; Domain Specs
;;----------------------------------------------------------------------

(s/def ::key keyword?)
(s/def ::arg? boolean?)
(s/def ::keyword-info (s/keys :req-un [::key ::arg?]))
(s/def ::query-token (s/or :string string?
                           :map    ::keyword-info))
(s/def ::query-tokens (s/coll-of ::query-token))

(s/def ::var-name symbol?)
(s/def ::var-meta-data (s/map-of keyword? any?))
(s/def ::var-info (s/keys :req-un [::var-name ::var-meta-data ::query-tokens]))
(s/def ::var-infos (s/coll-of ::var-info))




(def ^:private whitespace #{\newline \space \tab})

(def ^:private whitespace? (comp some? whitespace))


(def ^:private keyword-start? (partial = \:))

(def ^:private keyword-chars (set "-./0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"))
(def ^:private keyword-char? (comp some? keyword-chars))
(def ^:private allowed-chars-prior-kw-start (conj whitespace \{ \())

;;----------------------------------------------------------------------
;; Parsing Graphql Query to look for keywords
;; Strategy: Iterate over each char looking for things that might
;; be Clojure keywords and also if the keyword is an arg.
;; Input: '{ user(id: :id) {name email}}'
;; Output: ['{ user(id: ', {:key :id :arg? true}, ') {name email}}']
;; The output for the above example is a 3 element sequence of
;; [string map string]
;;----------------------------------------------------------------------
(defmulti ^:private on-query-char
  (fn [{:keys [in-kw? acc parens]} c]
    (let [prev-char (last acc)]
      (cond
        (and (not in-kw?)
             (keyword-start? c)
             (allowed-chars-prior-kw-start prev-char)) :keyword-start
        (and in-kw?
             (not (keyword-char? c))) :keyword-end
        (= \( c) :args-start
        (= \) c) :args-end
        :else :default))))

(defmethod on-query-char :keyword-start
  [{:keys [acc tokens] :as state} c] ; c is \:
  (assoc state :in-kw? true :acc [] :tokens (conj tokens (apply str acc))))

(defmethod on-query-char :keyword-end
  [{:keys [acc tokens parens] :as state} c]
  ;; NB c is *NOT* part of the keyword, it is part of the next accum

  ;(println "state in keyword-end" state)
  (when (keyword-start? c)
    (throw (ex-info "keyword-start encountered before properly ending previous keyword" {:state state :c c})))
  (let [info {:key (keyword (apply str acc))
              :arg? (not (empty? parens))}
        parens (if (= \) c)
                 (pop parens)
                 parens)]
    ;(println "parens: " (vec parens) "info: " info)

    (assoc state :in-kw? false :acc [c]
           :parens parens
           :tokens (conj tokens info))))

(defmethod on-query-char :args-start
  [state c]
  ;(println "state in args-start: %s" state)
  (-> (update state :parens conj c)
      (update :acc conj c)))

(defmethod on-query-char :args-end
  [state c]
  ;(println "state in args-end: %s" state)
  (-> (update state :parens pop)
      (update :acc conj c)))

(defmethod on-query-char :default
  [state c]
  (update state :acc conj c))


;;----------------------------------------------------------------------
(s/fdef tokenize-query
        :args (s/cat :s string?)
        :ret  (s/coll-of ::query-token))

(defn- tokenize-query
  "'Tokenize' to find clojure keywords.  Input is a string that represents
   a graphql query, output is a seq of either string or a map. Each map
   has `:key` and `:arg?` keys. The key `:key` is mapped to a clojure keyword
   and `:arg?` tells you if the keyword is inside a parameter list which requires
   serializing slightly differently especially for strings."
  [s]
  (let [{:keys [acc tokens in-kw?]} (reduce on-query-char
                                            {:in-kw? false
                                             :tokens []
                                             :acc []}
                                            (seq s))]
    (assert (not in-kw?) "Didn't expect input to end while still in a keyword")
    (conj tokens (apply str acc))))


;;----------------------------------------------------------------------
(defprotocol IFormatArg
  "Format arguments for graphql operations. Implementations for
   common Clojure types are provided such as String, maps, sequences,
   UUID, Keyword nil etc."
  (arg->str [arg] "Returns a string representation of `arg`."))

;;----------------------------------------------------------------------
(s/fdef map->str
        :args (s/cat :args map?)
        :ret string?)

(defn- map->str
  "Given a map of query arguments, formats them and concatenates to string.
  E.g. (map->str {:id 1 :type \"human\"}) => {id:1,type:\"human\"}"
  [args]
  (str "{"
       (->> (for [[k v] args]
              [(name k) ":" (arg->str v)])
            (interpose ",")
            flatten
            (apply str))
       "}"))

;;----------------------------------------------------------------------
(s/fdef seq->str
        :args (s/cat :args (s/or :seq seq?
                                 :sequential sequential?))
        :ret string?)


(defn- seq->str
  "Given something that is sequential format it to be like a JSON array."
  [arg]
  (str "[" (apply str (interpose "," (map arg->str arg))) "]"))

#?(:clj
   (extend-protocol IFormatArg
     nil
     (arg->str [arg] "null")
     String
     (arg->str [arg] (str "\"" arg "\""))
     UUID
     (arg->str [arg] (arg->str (str arg)))
     IPersistentMap
     (arg->str [arg] (map->str arg))
     IPersistentCollection
     (arg->str [arg] (seq->str arg))
     Keyword
     (arg->str [arg] (name arg))
     Object
     (arg->str [arg] (str arg))))

#?(:cljs
   (extend-protocol IFormatArg
     nil
     (arg->str [arg] "null")
     string
     (arg->str [arg] (str "\"" arg "\""))
     UUID
     (arg->str [arg] (arg->str (str arg)))
     PersistentArrayMap
     (arg->str [arg] (map->str arg))
     PersistentHashMap
     (arg->str [arg] (map->str arg))
     PersistentVector
     (arg->str [arg] (seq->str arg))
     IndexedSeq
     (arg->str [arg] (seq->str arg))
     LazySeq
     (arg->str [arg] (seq->str arg))
     List
     (arg->str [arg] (seq->str arg))
     Keyword
     (arg->str [arg] (name arg))
     number
     (arg->str [arg] (str arg))
     object
     (arg->str [arg] (str arg))
     boolean
     (arg->str [arg] (str arg))))




;;----------------------------------------------------------------------
(s/fdef substitute
        :args (s/cat :query-tokens (s/coll-of ::query-token) :m (s/map-of keyword? any?))
        :ret (s/coll-of string?))

(defn- substitute
  "query-tokens is a seq of strings or maps. each map in query-tokens indicates a
   replacement using `:key` as a lookup into map `m`."
  [query-tokens m]
  (let [tr-map (fn [{:keys [key arg?]}]
            (let [v (key m)]
              (when-not v
                (throw (ex-info (str "no replacement for key: " key) {:key key :m m})))
              (cond-> v
                arg? arg->str)))]
    (map (fn [x]
           (let [f (if (string? x) identity tr-map)]
             (f x)))
         query-tokens)))

;;----------------------------------------------------------------------
(defn hydrate-query-tokens
  "This should not be used by clients.
   This has to be public to be used in macros."
  [query-tokens m]
  (str/join (substitute query-tokens m)))

;;----------------------------------------------------------------------
(s/fdef hydrate
        :args (s/cat :query string? :m (s/map-of keyword? any?))
        :ret string?)

(defn hydrate
  "Takes a string with clojure keyword placeholders and a map with substitution values
   Replaces all occurrences of a keyword in s with values from map `m`. Returns the
   transformed string. If `m` does not contain a key an exception is thrown."
  [query m]
  (-> (tokenize-query query)
      (hydrate-query-tokens m)))




;;----------------------------------------------------------------------
;; Define vars from graphql file
;;----------------------------------------------------------------------
(def ^{:private true
       :const true} comment-def "# {")

(defn- safe-read-edn
  "Safe read edn returning the parsed structure or nil."
  [s]
  (try
    (edn/read-string s)
    #?(:clj
       (catch Exception ex
         (println "WARN: Not treating as edn: " s))
       :cljs
       (catch js/Error ex
         (println "WARN: Not treating as edn: " s)))))

(defn- line-state->def-info
  "Returns a map of def info if there's a key `:def` otherwise nil."
  [{:keys [acc] :as state}]
  (some-> state :def (assoc :query (str/join \newline acc))))

(defmulti ^:private on-line
  (fn [state line]
    (cond
      (str/starts-with? line comment-def) :def
      (str/starts-with? line "#") :comment
      :else :default)))

(defmethod on-line :def
  [state line]
  ;; line is a comment beginning with "^#", the rest of the line is parsed as edn and is
  ;; expected to be a map if it doesn't parse as edn then it is ignored incase graphql
  ;; code has been commented out
  (let [prev-def (line-state->def-info state)]
    ;; add prev-def to list of vars only if we hit a new def and successfully parsed that def
    ;; otherwise could just be a graphql comment
    (if-let [cur-def (safe-read-edn (subs line 1))]
      (cond-> (assoc state :def cur-def :acc [])
        prev-def (update :vars conj prev-def))
      state)))

(defmethod on-line :comment
  [state _]
  state)

(defmethod on-line :default
  [state line]
  (update state :acc conj line))

(s/fdef parse-var-defs*
        :args (s/cat :s string?)
        :ret coll?)

(defn- parse-var-defs*
  "Take a string, most likely representing the contents of a text file, separated by new lines
   and parse it for graphql queries to represent as vars. Returns a list of
   maps which represent information for constructing the vars."
  [s]
  (let [state (reduce on-line
                      {:vars [] :acc []}
                      (str/split-lines s))
        last-def (line-state->def-info state)]
    (cond-> (:vars state)
      last-def (conj last-def))))

(defn- normalized-var-info
  [{:keys [name name- doc query] :or {doc ""}}]
  (assert (or name name-))
  (let [[name private?] (if name
                          [name false]
                          [name- true])]
    {:var-name name
     :var-meta-data {:private private? :doc doc}
     :query-tokens (tokenize-query query)}))


(defn- var-info->defn
  [{:keys [var-name var-meta-data query-tokens]}]
  `(defn ~(vary-meta var-name merge var-meta-data)
     [params#]
     (hydrate-query-tokens ~query-tokens params#)))


#?(:clj
   (do
     (defn- parse-var-defs
       "Returns a list of maps with keys `:name` or `:name-`, `:query` and perhaps `:doc`"
       [file]
       (let [f (condp instance? file
                 java.io.File file
                 java.net.URL file
                 (or (io/resource file)
                     (throw (ex-info (str "Unable to read graphql query file: " file) {:file file}))))]
         (-> f slurp parse-var-defs*)))

     (s/fdef defqueries
             :args (s/cat :file (s/or :string string?
                                      :file (partial instance? java.io.File)
                                      :uri uri?)))
     (defmacro defqueries
       "Identifies queries in `file` and interns vars in the namespace that this is called from."
       [file]
       (let [vars (->> (parse-var-defs file)
                       (map normalized-var-info)
                       (map var-info->defn))]
         `(do ~@vars)))))
