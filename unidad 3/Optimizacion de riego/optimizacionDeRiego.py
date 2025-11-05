################################################################################
# --- SECCIÓN 1: IMPORTAR HERRAMIENTAS (LIBRERÍAS) ---
################################################################################
#
# En Python, no podemos hacer nada sin "importar" las herramientas
# - pandas (pd): Piensa en 'pd' como "Excel para Python". Lo usamos para
#   cargar nuestra tabla de 100 datos.
# - numpy (np): Es la "Calculadora Científica" de Python. Es súper rápida
#   para operaciones matemáticas con listas y matrices (arrays).
# - io: Una pequeña herramienta que nos permite "engañar" a pandas para
#   que lea un bloque de texto (string) como si fuera un archivo.
# - pyswarms (ps): ¡La estrella del show! Esta es la librería que
#   contiene el algoritmo de Enjambre de Partículas (PSO).
# - MinMaxScaler: Es la librería principal para "dibujar"
#   gráficas en Python.
# - seaborn (sns):Hace que las
#   gráficas se vean más bonitas y modernas.
# - plot_cost_history: Una función "extra" que nos regala 'pyswarms'
#   para dibujar fácilmente la gráfica de convergencia.

import pandas as pd
import numpy as np
import io
import pyswarms as ps
from sklearn.preprocessing import MinMaxScaler
import matplotlib.pyplot as plt
import seaborn as sns
from pyswarms.utils.plotters import plot_cost_history

################################################################################
# --- SECCIÓN 2: CARGAR LOS DATOS ---
################################################################################
def cargar_datos_terreno():
    """
    Carga los 100 puntos de datos del proyecto en una tabla (DataFrame).
    """
    print("--- 2. Cargando datos del terreno... ---")
    
    # datos copiados del documento.
    data_string = """Humedad (%),Cultivo,Elevación (m),Salinidad (dS/m),Temperatura (°C),Latitud,Longitud
    35.4,Maíz,32.1,1.18,30.9,25.525269,-108.480884
    20.0,Maíz,40.8,3.3,27.5,25.600618,-108.519701
    43.3,Maíz,21.9,1.17,31.8,25.577411,-108.465669
    36.3,Maíz,41.7,2.93,25.3,25.616337,-108.470516
    17.3,Chile,31.8,1.91,29.1,25.522286,-108.480179
    22.0,Chile,32.6,1.62,35.2,25.569703,-108.501312
    19.1,Chile,21.4,3.9,25.7,25.579862,-108.510461
    28.3,Tomate,18.1,0.54,37.0,25.536138,-108.45039
    20.1,Chile,36.7,2.54,25.9,25.543936,-108.464508
    6.0,Maíz,31.9,0.85,33.2,25.568602,-108.436378
    41.7,Chile,45.1,2.52,21.6,25.615186,-108.428339
    19.5,Tomate,12.2,2.18,28.8,25.601332,-108.469617
    22.3,Maíz,34.2,1.35,34.5,25.57396,-108.487928
    11.0,Chile,49.4,3.75,35.0,25.551895,-108.489976
    40.8,Chile,31.9,1.32,20.7,25.612299,-108.422137
    6.9,Tomate,20.7,1.8,22.7,25.562234,-108.464749
    20.6,Maíz,46.0,2.39,38.8,25.544452,-108.430058
    32.3,Tomate,29.9,2.22,25.4,25.610699,-108.480493
    39.2,Tomate,25.0,1.66,29.0,25.617916,-108.447962
    25.9,Maíz,42.2,0.56,30.1,25.566207,-108.509164
    9.2,Maíz,39.8,2.23,24.9,25.577242,-108.452329
    8.1,Maíz,36.7,1.98,32.5,25.601426,-108.507991
    28.2,Maíz,10.6,0.95,38.8,25.578539,-108.502035
    5.5,Tomate,23.1,1.49,25.9,25.545519,-108.503949
    16.2,Chile,14.4,2.97,22.8,25.585059,-108.452106
    11.2,Tomate,16.9,1.5,32.4,25.576974,-108.502348
    39.6,Tomate,18.7,3.92,20.7,25.597132,-108.476836
    21.0,Chile,11.1,3.25,26.9,25.530344,-108.485571
    8.2,Chile,23.9,1.28,30.6,25.56348,-108.445757
    13.9,Tomate,21.2,0.64,26.2,25.593413,-108.491781
    16.2,Chile,22.4,0.66,36.5,25.571965,-108.492518
    43.3,Tomate,45.6,1.56,22.0,25.615266,-108.487209
    43.3,Chile,49.7,1.9,34.8,25.558179,-108.518102
    29.6,Maíz,18.2,1.17,29.1,25.568482,-108.442325
    18.9,Maíz,29.9,2.02,36.8,25.618887,-108.518957
    32.6,Tomate,35.0,0.96,27.7,25.576783,-108.517817
    31.7,Chile,23.4,1.17,25.5,25.568994,-108.472747
    10.7,Maíz,10.3,2.44,25.3,25.614042,-108.493219
    42.7,Chile,10.1,1.36,35.3,25.600457,-108.496185
    22.9,Maíz,20.1,2.92,29.5,25.615359,-108.497118
    29.1,Chile,12.3,2.89,23.0,25.556258,-108.430394
    33.2,Chile,14.0,0.78,29.8,25.540828,-108.460249
    35.6,Chile,44.6,3.56,30.5,25.607947,-108.464478
    9.5,Maíz,41.4,2.6,34.8,25.541347,-108.453822
    13.6,Chile,40.7,0.94,26.6,25.53381,-108.519463
    42.0,Chile,43.6,2.18,25.2,25.570288,-108.449353
    25.6,Chile,11.0,1.84,25.5,25.594593,-108.509253
    33.2,Tomate,18.9,3.0,26.4,25.61416,-108.455096
    44.3,Tomate,24.9,3.88,27.4,25.603546,-108.489592
    35.9,Chile,13.1,1.28,30.6,25.59215,-108.519166
    31.1,Chile,34.3,0.74,26.6,25.599119,-108.497474
    9.9,Tomate,20.7,0.6,26.5,25.541617,-108.420215
    24.2,Tomate,20.1,0.94,36.6,25.546029,-108.501044
    40.8,Tomate,45.8,0.96,31.5,25.601962,-108.439458
    11.2,Maíz,30.7,1.49,28.1,25.590474,-108.495022
    29.1,Maíz,27.9,2.99,35.8,25.541885,-108.438006
    37.7,Tomate,10.2,3.84,27.5,25.566371,-108.471062
    35.7,Maíz,11.4,3.89,24.7,25.577648,-108.496633
    23.9,Maíz,44.9,1.81,28.3,25.603601,-108.44998
    40.2,Chile,11.1,1.32,25.2,25.559236,-108.518247
    6.3,Maíz,21.7,1.32,40.0,25.551866,-108.445206
    44.9,Chile,14.2,1.52,36.1,25.533315,-108.423948
    43.1,Maíz,25.7,1.61,37.2,25.544761,-108.468132
    31.3,Tomate,39.9,0.87,32.5,25.579027,-108.490022
    11.9,Chile,40.0,3.25,29.4,25.61422,-108.448072
    22.5,Chile,22.2,2.33,25.7,25.547955,-108.450106
    23.1,Chile,13.9,3.02,22.0,25.523878,-108.502427
    31.2,Tomate,30.0,1.34,30.6,25.533115,-108.456761
    20.0,Maíz,15.3,2.13,29.1,25.6049,-108.492247
    10.3,Maíz,42.1,2.92,28.2,25.604552,-108.517331
    21.4,Maíz,45.1,0.55,35.5,25.561935,-108.519274
    26.9,Maíz,32.3,3.4,25.7,25.555427,-108.42319
    22.5,Chile,11.8,1.37,32.4,25.52675,-108.510972
    40.9,Chile,32.0,3.11,28.3,25.612885,-108.484575
    19.0,Tomate,43.9,3.19,38.1,25.611875,-108.508331
    5.1,Chile,26.5,1.8,20.3,25.579335,-108.467927
    19.9,Maíz,26.1,3.81,35.5,25.576945,-108.502777
    27.9,Maíz,37.6,0.88,31.5,25.611043,-108.508468
    14.6,Tomate,45.0,1.12,24.7,25.531398,-108.506489
    33.7,Chile,22.0,1.19,22.1,25.548237,-108.516646
    15.9,Tomate,30.1,3.3,23.1,25.554671,-108.501926
    44.8,Maíz,31.6,2.13,34.7,25.521859,-108.513298
    26.6,Maíz,38.7,1.25,32.8,25.613286,-108.490537
    26.7,Tomate,41.5,3.21,33.8,25.559703,-108.441534
    6.4,Maíz,47.4,3.57,36.4,25.544613,-108.504298
    12.7,Maíz,34.3,1.06,30.8,25.544125,-108.453288
    22.3,Tomate,35.1,3.19,20.0,25.539745,-108.45725
    27.1,Tomate,46.7,2.02,38.7,25.574685,-108.44159
    24.6,Chile,32.2,1.84,32.8,25.544383,-108.422493
    31.2,Tomate,28.8,3.22,31.0,25.582556,-108.505056
    31.5,Chile,39.3,3.95,38.9,25.539601,-108.468409
    10.7,Tomate,37.8,2.88,30.1,25.578598,-108.509114
    35.6,Tomate,49.4,3.68,38.0,25.589127,-108.497769
    9.9,Maíz,23.5,3.07,25.1,25.528027,-108.472023
    26.2,Chile,44.3,2.69,38.1,25.537541,-108.497964
    29.7,Tomate,14.5,1.54,28.3,25.567005,-108.462403
    28.9,Maíz,39.9,1.64,31.4,25.533387,-108.472275
    25.5,Maíz,37.8,0.87,20.8,25.555069,-108.457875
    17.5,Chile,23.3,1.58,22.3,25.569962,-108.45769
    12.5,Tomate,43.7,1.65,23.3,25.570974,-108.420756
    """
    df = pd.read_csv(io.StringIO(data_string))
    # La función "devuelve" la tabla 'df' para que el resto
    # del script pueda usarla.
    return df
################################################################################
# --- SECCIÓN 3: PREPARAR LOS DATOS (NORMALIZACIÓN) ---
################################################################################
# "Normalizar" es crucial para que el algoritmo funcione bien.

def preparar_datos_para_pso(df):
    """
    "Limpia" los datos. Selecciona las 5 columnas clave y las
    "normaliza" (escala todos los valores para que quepan entre 0 y 1).
    """
    print("--- 3. Preparando datos (Normalizando)... ---")
    # 1. Definimos las columnas (features) que usaremos para
    #    encontrar la "variabilidad" del terreno.
    #    Elegimos estas 5 basándonos en la descripción del proyecto.
    features = ['Latitud', 'Longitud', 'Elevación (m)', 'Salinidad (dS/m)', 'Humedad (%)']
    
    # 2. Creamos una nueva tabla solo con esas 5 columnas.
    datos_para_optimizar = df[features]

    #    Así, todas las 5 columnas "pesan" lo mismo para el algoritmo.
    
    # 4. Creamos el "aplastador" (el objeto scaler)
    scaler = MinMaxScaler()
    
    # 5. Usamos el scaler para "aplastar" (transformar) nuestros datos.
    datos_normalizados = scaler.fit_transform(datos_para_optimizar)
    
    # 6. Devolvemos 3 cosas:
    #    - datos_normalizados: La tabla con números entre 0 y 1.
    #    - scaler: El "aplastador", para poder "des-aplastar" los resultados al final.
    #    - features: La lista de nombres de las columnas, para usarla en las tablas finales.
    return datos_normalizados, scaler, features


################################################################################
# --- SECCIÓN 4: EL "JUEZ" (FUNCIÓN DE APTITUD O COSTO) ---
################################################################################
#
# 
# PSO es un "minimizador": siempre busca la solución con la
# calificación (costo) MÁS BAJA.
#
# Esta función es el q dice a PSO qué tan "malo"
# (costoso) es cada intento.

def calcular_costo_total(swarm, datos_normalizados, n_sensores, n_variables):
    """
    Calcula la calificación (costo) para CADA partícula (solución)
    en el enjambre (swarm).
    
    Parámetros:
    - swarm: Un array de NumPy (ej. 50x25) donde cada FILA es una
             partícula (una solución candidata completa).
    - datos_normalizados: Nuestros 100 puntos de datos "aplastados".
    - n_sensores: Cuántos sensores buscamos (ej. 5).
    - n_variables: Cuántas variables tiene cada sensor (ej. 5).
    """
    
    # ¿Cuántas partículas (soluciones) hay que calificar?
    n_particulas = swarm.shape[0] 
    
    # Creamos una lista de ceros para guardar las 50 calificaciones.
    costos_totales = np.zeros(n_particulas) 

    # --- Bucle 1: Repetir para cada partícula (de 0 a 49) ---
    for i in range(n_particulas):
        
        # Tomar la solución de la partícula 'i'.
        # Es una "tira" larga de números (ej. 25 números si son 5x5).
        posicion_particula = swarm[i] 
        
        # "Remodelar" esa "tira" de 25 números en una "tablita" de 5x5.
        # Ahora 'sensores' es una lista de 5 sensores,
        # y cada sensor tiene 5 variables.
        sensores = posicion_particula.reshape(n_sensores, n_variables)
        
        costo_de_esta_particula = 0 # Calificación inicial = 0

        # --- Bucle 2: Repetir para cada uno de los 100 puntos de datos ---
        for punto_dato in datos_normalizados:
            
            # 'punto_dato' es una lista de 5 números (Lat, Lon, etc.)
        
            # 'distancias' será una lista de 5 números (distancia al s1, al s2, ... al s5).
            distancias = np.linalg.norm(sensores - punto_dato, axis=1)
            
            # Encontrar la distancia MÁS CORTA.
            # ¿Cuál de los 5 sensores le queda más cerca a este 'punto_dato'?
            distancia_minima = np.min(distancias)
        
            costo_de_esta_particula += distancia_minima**2
        
        # --- Fin del Bucle 2 ---
        
        # Guardar la calificación final (la suma de las 100 distancias)
        # para la partícula 'i'.
        costos_totales[i] = costo_de_esta_particula
        
    # --- Fin del Bucle 1 ---
        
    # Devolver la lista con las 50 calificaciones.
    return costos_totales


################################################################################
# --- SECCIÓN 5: EJECUTAR EL ENJAMBRE (PSO) ---
################################################################################
#
# Esta función crea el enjambre de partículas y lo pone a "volar"
# (optimizar) 

def correr_optimizacion_pso(datos_normalizados, n_sensores, n_variables):
    """
    Configura y corre el algoritmo PSO para encontrar la mejor solución.
    """
    print("--- 4. Configurando el enjambre PSO... ---")
    
    # 1. ¿De qué tamaño es el problema?
    # Una partícula = 5 sensores * 5 variables = 25 números.
    # Este es el "espacio de búsqueda".
    dimensiones_problema = n_sensores * n_variables
    
    # 2. "Personalidad" de las partículas (Hiperparámetros)
    # Estos son valores estándar que funcionan bien casi siempre.
    # 'c1' (cognitivo): Qué tanto confía la partícula en su *propia* mejor solución.
    # 'c2' (social): Qué tanto confía la partícula en la *mejor solución del grupo*.
    # 'w' (inercia): Qué tanto "impulso" mantiene de su movimiento anterior.
    opciones = {'c1': 0.5, 'c2': 0.3, 'w': 0.9}

    # 3. Límites (Bounds)
    # Le decimos a PSO que las partículas SOLO pueden buscar soluciones
    # con números entre 0 y 1 (porque "aplastamos" nuestros datos).
    limites = (np.zeros(dimensiones_problema), np.ones(dimensiones_problema))

    # 4. Crear el Enjambre
    n_particulas = 50   # 50 "agentes" (partículas) buscando
    n_iteraciones = 200 # 200 "rondas" de búsqueda
    
    # Creamos el objeto "optimizador" usando la clase 'GlobalBestPSO'.
    # "GlobalBest" significa que TODAS las partículas se comunican
    # con la MEJOR partícula de todo el enjambre.
    optimizer = ps.single.GlobalBestPSO(
        n_particles=n_particulas,
        dimensions=dimensiones_problema,
        options=opciones,
        bounds=limites
    )
    # Nuestra función "juez" (calcular_costo_total) necesita información
    # extra (los 100 puntos de datos, n_sensores, etc.).
    # Los metemos en un "diccionario".
    mochila_de_datos = {
        'datos_normalizados': datos_normalizados,
        'n_sensores': n_sensores,
        'n_variables': n_variables
    }

    # 6. A VOLAR(Ejecutar la optimización)
    print(f"--- 5. ¡Optimizando! ({n_particulas} partículas, {n_iteraciones} rondas)... ---")
    
    # Le decimos al optimizador:
    # - Usa 'calcular_costo_total'.
    # - Hazlo por 'n_iteraciones' rondas.
    # - Pása todo lo que está en la 'mochila_de_datos'.
    costo_final, solucion_final = optimizer.optimize(
        calcular_costo_total,
        iters=n_iteraciones,
        **mochila_de_datos 
    )
    
    print(f"--- 6. ¡Optimización terminada! Costo final: {costo_final} ---")
    
    # Devolvemos la MEJOR solución (solucion_final)
    # y el historial de calificaciones (optimizer.cost_history).
    return solucion_final, optimizer.cost_history


################################################################################
# --- SECCIÓN 6: MOSTRAR LOS RESULTADOS ---
################################################################################

def mostrar_resultados_finales(solucion_final, scaler, features, n_sensores, n_variables):
    """
    Toma la 'solucion_final' (normalizada) y la "des-normaliza"
    para mostrar los valores reales (Latitud, Elevación, etc.)
    """
    print("\n--- 7. Procesando la solución ganadora... ---")
    
    # 1. Reordenar la "tira" de 25 números a una "tablita" de 5x5
    sensores_normalizados = solucion_final.reshape(n_sensores, n_variables)
    
    # 2. "Des-aplastar" los datos (Invertir la Normalización)
    # Usamos el 'scaler' que guardamos en la Sección 3 para
    # convertir los números de [0, 1] de vuelta a sus valores reales.
    sensores_reales = scaler.inverse_transform(sensores_normalizados)
    
    # 3. Poner los resultados en una tabla 'pandas' para que se vea bonita.
    df_sensores = pd.DataFrame(sensores_reales, columns=features)
    # Le ponemos nombres a las filas (Sensor 1, Sensor 2, ...)
    df_sensores.index = [f'Sensor {i+1}' for i in range(n_sensores)]
    
    print("\n---  ¡RESULTADO FINAL UBICACIONES ÓPTIMAS ---")
    print(df_sensores) 
    
    # Devuelve la tabla para que la usemos en las gráficas
    return df_sensores


################################################################################
# --- SECCIÓN 7: DIBUJAR LAS GRÁFICAS (ENTREGABLES) ---
################################################################################

def dibujar_graficas(df_original, df_sensores, historial_costo, n_sensores):
    """
    Crea y muestra las dos gráficas clave:
    1. El mapa geoespacial con los sensores.
    2. La curva de convergencia (costo vs. iteraciones).
    """
    print("--- 8. Generando gráficas (se abrirán en ventanas nuevas)... ---")

    # --- GRÁFICA 1: El Mapa Geoespacial ---
    # Creamos una "figura" (un lienzo en blanco)
    plt.figure(figsize=(12, 8))
    
    # Usamos 'seaborn' (sns) para dibujar los 100 puntos de datos
    sns.scatterplot(
        data=df_original, # Usa la tabla original (con nombres de cultivos)
        x='Longitud',
        y='Latitud',
        hue='Cultivo', # Coloréalos según la columna "Cultivo"
        style='Cultivo' # Dales una forma (círculo, X, cuadrado) según el cultivo
    )
    
    # Encima del mismo gráfico, dibujamos nuestras 5 "Estrellas" (sensores)
    sns.scatterplot(
        data=df_sensores, # Usa la tabla de resultados
        x='Longitud',
        y='Latitud',
        color='black',       # Color negro
        marker='*',          # Forma de estrella
        s=500,               # Tamaño grande
        label='Sensor Óptimo (PSO)' # Etiqueta para la leyenda
    )
    plt.title(f'Mapa Geoespacial: {n_sensores} Sensores Optimizados')
    plt.grid(True) # Poner una cuadrícula de fondo

    # --- GRÁFICA 2: La Curva de Convergencia ---
    # Esta gráfica prueba que el algoritmo "aprendió".
    # Muestra cómo la calificación (costo) fue bajando en cada ronda.
    # Usamos la función especial de 'pyswarms'
    plot_cost_history(cost_history=historial_costo)
    plt.title('Curva de Convergencia (Cómo "aprendió" el enjambre)')
    plt.xlabel('Ronda (Iteración)')
    plt.ylabel('Calificación (Costo Total)')
    plt.grid(True)

    # --- Mostrar AMBAS gráficas ---
    # Este comando abre las ventanas emergentes con los gráficos.
    plt.show()


################################################################################
# --- SECCIÓN 8: EL SCRIPT PRINCIPAL (LA "RECETA" MAESTRA) ---
################################################################################


print("--- INICIO DEL PROYECTO DE OPTIMIZACIÓN PSO ---")

# --- PASO A: Definir cuántos sensores queremos ---
N_SENSORES = 5 # Puedes cambiar este número (ej. 3 o 8) y correr de nuevo

# --- PASO B: Cargar los datos ---
# Llama a la función de la Sección 2
df_original = cargar_datos_terreno()

# --- PASO C: Preparar los datos ---
# Llama a la función de la Sección 3
datos_normalizados, scaler_guardado, nombres_features = preparar_datos_para_pso(df_original)

# --- PASO D: Definir cuántas variables (columnas) tenemos ---
# Contamos cuántas columnas hay en nuestros datos normalizados (serán 5)
N_VARIABLES = datos_normalizados.shape[1] 

# --- PASO E: Correr la optimización ---
# Llama a la función de la Sección 5 (el paso más largo)
solucion_ganadora, historial_costo = correr_optimizacion_pso(
    datos_normalizados, 
    N_SENSORES, 
    N_VARIABLES
)

# --- PASO F: Mostrar la tabla de resultados ---
# Llama a la función de la Sección 6
df_sensores_finales = mostrar_resultados_finales(
    solucion_ganadora, 
    scaler_guardado, # El "aplastador"
    nombres_features, # La lista de nombres
    N_SENSORES, 
    N_VARIABLES
)

# --- PASO G: Dibujar las gráficas ---
# Llama a la función de la Sección 7
dibujar_graficas(
    df_original, # La tabla original (para los colores de cultivos)
    df_sensores_finales, # La tabla de resultados (para las estrellas)
    historial_costo, # La lista de calificaciones (para la curva)
    N_SENSORES
)

print("\n--- FIN DEL PROYECTO ---")
