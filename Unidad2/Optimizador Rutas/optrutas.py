import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import geopandas
import contextily as cx
import random
import math
import io
import os
from sklearn.cluster import KMeans

print("Librerías importadas. Iniciando la solución logística completa con K-Means...")

# Recocido simulado para optimización de rutas
def recocido_simulado(nodes, cost_matrix, temp_inicial=1000, factor_enfriamiento=0.995, iteraciones_por_temp=100):
    if not nodes:
        return [], 0
    solucion_actual = random.sample(nodes, len(nodes))
    def calcular_costo_ruta(route, cost_matrix):
        total_cost = 0
        full_route = [0] + route + [0]
        for i in range(len(full_route) - 1):
            total_cost += cost_matrix[full_route[i], full_route[i+1]]
        return total_cost
    costo_actual = calcular_costo_ruta(solucion_actual, cost_matrix)
    mejor_solucion, mejor_costo = solucion_actual, costo_actual
    temperatura = temp_inicial
    while temperatura > 0.1:
        for _ in range(iteraciones_por_temp):
            vecino = solucion_actual[:]
            if len(vecino) > 1:
                i, j = random.sample(range(len(vecino)), 2)
                vecino[i], vecino[j] = vecino[j], vecino[i]
            costo_vecino = calcular_costo_ruta(vecino, cost_matrix)
            delta_costo = costo_vecino - costo_actual
            if delta_costo < 0 or random.uniform(0, 1) < math.exp(-delta_costo / temperatura):
                solucion_actual, costo_actual = vecino, costo_vecino
                if costo_actual < mejor_costo:
                    mejor_solucion, mejor_costo = solucion_actual, costo_actual
        temperatura *= factor_enfriamiento
    return mejor_solucion, mejor_costo

# Función principal
def main():
    locations_file = "datos_distribucion_tiendas.xlsx"
    distances_file = "matriz_distancias.xlsx"

    try:
        print(f"Cargando datos desde '{locations_file}' y '{distances_file}'...")
        
        df_locations_full = pd.read_excel(locations_file)
        dist_matrix_full = pd.read_excel(distances_file, header=0, index_col=0).to_numpy()
        
        # Verificar consistencia de datos
        num_locs = len(df_locations_full)
        num_dists_rows, num_dists_cols = dist_matrix_full.shape

        # Determinar el tamaño consistente más pequeño.
        # La matriz de distancias debe ser cuadrada.
        min_size = min(num_locs, num_dists_rows, num_dists_cols)

        if min_size < num_locs or min_size < num_dists_rows:
            print(f"La tabla de locaciones tiene {num_locs} nodos.")
            print(f"La matriz de distancias es de tamaño {num_dists_rows}x{num_dists_cols}.")
            print(f"El análisis se limitará a los primeros {min_size} nodos para evitar errores.")
            
            df_locations = df_locations_full.iloc[:min_size].copy()
            dist_matrix = dist_matrix_full[:min_size, :min_size]
        else:
            df_locations = df_locations_full
            dist_matrix = dist_matrix_full

        costo_por_km = 2.3
        fuel_matrix = dist_matrix * costo_por_km
        df_locations['Node_Index'] = range(len(df_locations))
        centers = df_locations[df_locations['Tipo'] == 'Centro de Distribución'].reset_index(drop=True)
        stores = df_locations[df_locations['Tipo'] == 'Tienda'].copy()
        print("Datos cargados y matrices generadas.")

        print("Asignando tiendas a cuadrantes con K-Means...")
        initial_centroids = centers[['Longitud_WGS84', 'Latitud_WGS84']].to_numpy()
        store_coords = stores[['Longitud_WGS84', 'Latitud_WGS84']].to_numpy()
        kmeans = KMeans(n_clusters=len(centers), init=initial_centroids, n_init=1, random_state=42)
        stores['cluster'] = kmeans.fit_predict(store_coords)
        clusters = {int(center_idx): [] for center_idx in centers['Node_Index']}
        for _, store_data in stores.iterrows():
            cluster_label = store_data['cluster']
            center_node_index = centers['Node_Index'].iloc[cluster_label]
            clusters[center_node_index].append(int(store_data['Node_Index']))
        
        print("Optimizando rutas para cada cuadrante...")
        all_routes = []
        total_distancia_general = 0
        total_combustible_general = 0

        for center_idx, assigned_stores_indices in clusters.items():
            center_name = df_locations.loc[center_idx]['Nombre']
            if not assigned_stores_indices:
                print(f"- {center_name} no tiene tiendas asignadas, se omite.")
                continue
            
            print(f"- Procesando ruta para {center_name} ({len(assigned_stores_indices)} tiendas)...")
            
            route_nodes_indices = [center_idx] + assigned_stores_indices
            
            sub_dist_matrix = np.zeros((len(route_nodes_indices), len(route_nodes_indices)))
            for i in range(len(route_nodes_indices)):
                for j in range(len(route_nodes_indices)):
                    orig_i = route_nodes_indices[i]
                    orig_j = route_nodes_indices[j]
                    sub_dist_matrix[i, j] = dist_matrix[orig_i, orig_j]
            
            nodes_to_visit = list(range(1, len(route_nodes_indices)))
            mejor_ruta_sub, costo_distancia = recocido_simulado(nodes_to_visit, sub_dist_matrix)
            
            optimized_route_indices = [center_idx] + [route_nodes_indices[i] for i in mejor_ruta_sub] + [center_idx]
            
            costo_combustible = 0
            for i in range(len(optimized_route_indices) - 1):
                costo_combustible += fuel_matrix[optimized_route_indices[i], optimized_route_indices[i+1]]

            all_routes.append({
                "center_name": center_name,
                "route_indices": optimized_route_indices,
                "distancia": costo_distancia,
                "combustible": costo_combustible
            })
            total_distancia_general += costo_distancia
            total_combustible_general += costo_combustible

        # Se genera el mapa con las rutas
        print("Generando mapa con todas las rutas...")
        gdf = geopandas.GeoDataFrame(df_locations, geometry=geopandas.points_from_xy(df_locations.Longitud_WGS84, df_locations.Latitud_WGS84), crs="EPSG:4326")
        gdf_web = gdf.to_crs(epsg=3857)
        fig, ax = plt.subplots(figsize=(15, 15))
        gdf_web[gdf_web['Tipo'] == 'Tienda'].plot(ax=ax, color='blue', markersize=50, alpha=0.7, edgecolor='k', zorder=3)
        gdf_web[gdf_web['Tipo'] == 'Centro de Distribución'].plot(ax=ax, color='red', marker='s', markersize=150, edgecolor='k', zorder=5)
        
        # Asigna un color diferente a cada ruta para distinguirlas mejor
        colors = plt.cm.get_cmap('tab10', len(all_routes))
        for idx, route_info in enumerate(all_routes):
            if not route_info["route_indices"]: continue
            route_gdf = gdf_web.loc[route_info["route_indices"]]
            route_x = [point.x for point in route_gdf.geometry]
            route_y = [point.y for point in route_gdf.geometry]
            ax.plot(route_x, route_y, color=colors(idx), linewidth=2.5, linestyle='-', zorder=2, alpha=0.8)
            

        # Generamos el mapa base
        cx.add_basemap(ax, source=cx.providers.OpenStreetMap.Mapnik, zoom=12)
        ax.set_title("Plan Logístico Completo con Clustering K-Means", fontsize=20)
        ax.axis('off')
        from matplotlib.lines import Line2D
        legend_elements = [
            Line2D([0], [0], marker='s', color='w', label='Centros de Distribución', markerfacecolor='red', markersize=15),
            Line2D([0], [0], marker='o', color='w', label='Tiendas', markerfacecolor='blue', markersize=12),
            Line2D([0], [0], color='gray', lw=2, label='Rutas Optimizadas')
        ]
        ax.legend(handles=legend_elements, loc='upper left', fontsize='x-large', frameon=True, facecolor='white', framealpha=0.8)
        plt.savefig('mapa_logistico_kmeans.png', dpi=300, bbox_inches='tight')
        

        print("Se ha generado el archivo 'mapa_logistico_kmeans.png'.")
        print("********* PLAN DE RUTAS *********")

        all_routes.sort(key=lambda x: x['center_name'])
        for route_info in all_routes:
            if route_info['distancia'] > 0:
                print(f"\n- Ruta para: {route_info['center_name']}")
                print(f"  - Tiendas a visitar: {len(route_info['route_indices']) - 2}")
                print(f"  - Distancia de la ruta: {route_info['distancia']:.2f} km")
                print(f"  - Costo de combustible estimado: ${route_info['combustible']:.2f} MXN")
                print(f"**************************************************************************")
        
        print("\n--- Totales de la Operación ---")
        print(f"Distancia Total General: {total_distancia_general:.2f} km")
        print(f"Costo Total de Combustible: ${total_combustible_general:.2f} MXN")

    # Si no encuentra un archivo
    except FileNotFoundError as e:
        print("\n--- ERROR ---")
        print(f"No se pudo encontrar un archivo de datos necesario: {e}")
    # Si hay un error en algún valor
    except Exception as e:
        print(f"Algo pasó: {e}")

if __name__ == "__main__":
    main()
