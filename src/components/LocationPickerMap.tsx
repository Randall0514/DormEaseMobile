import { useEffect, useMemo, useRef, useState } from 'react';
import { MapContainer, TileLayer, Marker, useMap, useMapEvents } from 'react-leaflet';
import L from 'leaflet';

// Fix default marker icon (Leaflet's asset path breaks with bundlers)
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

delete (L.Icon.Default.prototype as unknown as Record<string, unknown>)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
});

interface LocationPickerMapProps {
  value?: { lat: number; lng: number };
  onChange?: (location: { lat: number; lng: number; address: string }) => void;
}

function DraggableMarker({
  position,
  onDragEnd,
}: {
  position: L.LatLngExpression;
  onDragEnd: (latlng: L.LatLng) => void;
}) {
  const markerRef = useRef<L.Marker>(null);

  const eventHandlers = useMemo(
    () => ({
      dragend() {
        const marker = markerRef.current;
        if (marker) onDragEnd(marker.getLatLng());
      },
    }),
    [onDragEnd],
  );

  return <Marker draggable position={position} ref={markerRef} eventHandlers={eventHandlers} />;
}

function MapClickHandler({ onClick }: { onClick: (latlng: L.LatLng) => void }) {
  useMapEvents({ click: (e) => onClick(e.latlng) });
  return null;
}

// Force the map to recalculate its size when rendered inside a modal/hidden container.
// Uses ResizeObserver to detect when the container actually reaches its final size.
function ResizeHandler() {
  const map = useMap();
  useEffect(() => {
    const container = map.getContainer();

    // Observe container resizes (catches modal animation completing)
    const observer = new ResizeObserver(() => {
      map.invalidateSize();
    });
    observer.observe(container);

    // Also fire multiple times during typical modal animation (0–500ms)
    const timers = [100, 300, 500, 1000].map((ms) =>
      setTimeout(() => map.invalidateSize(), ms),
    );

    return () => {
      observer.disconnect();
      timers.forEach(clearTimeout);
    };
  }, [map]);
  return null;
}

export default function LocationPickerMap({ value, onChange }: LocationPickerMapProps) {
  // Default center: Dagupan City, Pangasinan, Philippines
  const defaultCenter: L.LatLngTuple = [16.0433, 120.3334];
  const [position, setPosition] = useState<L.LatLngTuple>(
    value ? [value.lat, value.lng] : defaultCenter,
  );

  useEffect(() => {
    if (value) setPosition([value.lat, value.lng]);
  }, [value]);

  const reverseGeocode = async (lat: number, lng: number) => {
    try {
      const res = await fetch(
        `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`,
        { headers: { 'Accept-Language': 'en' } },
      );
      const data = await res.json();
      return (data.display_name as string) || `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
    } catch {
      return `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
    }
  };

  const handlePositionChange = async (latlng: L.LatLng) => {
    const newPos: L.LatLngTuple = [latlng.lat, latlng.lng];
    setPosition(newPos);
    const address = await reverseGeocode(latlng.lat, latlng.lng);
    onChange?.({ lat: latlng.lat, lng: latlng.lng, address });
  };

  return (
    <MapContainer
      center={position}
      zoom={13}
      style={{ height: 300, width: '100%', borderRadius: 8 }}
      scrollWheelZoom
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <DraggableMarker position={position} onDragEnd={handlePositionChange} />
      <MapClickHandler onClick={handlePositionChange} />
      <ResizeHandler />
    </MapContainer>
  );
}
