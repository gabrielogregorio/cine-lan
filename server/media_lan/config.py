"""Settings read from the environment."""
import ipaddress
import os


class Config:
    def __init__(self):
        self.root = os.path.realpath(os.environ.get("MEDIA_LAN_ROOT", "/media/ssd/data"))
        self.movies_dir = os.environ.get("MEDIA_LAN_MOVIES_DIR", "movies")
        self.shows_dir = os.environ.get("MEDIA_LAN_SHOWS_DIR", "shows")
        self.database = os.environ.get("MEDIA_LAN_DATABASE", "media-lan.sqlite")
        self.address = os.environ.get("MEDIA_LAN_ADDRESS", "0.0.0.0")
        self.port = int(os.environ.get("MEDIA_LAN_PORT", "8097"))
        self.scan_interval_s = float(os.environ.get("MEDIA_LAN_SCAN_INTERVAL_S", "300"))
        networks = os.environ.get("MEDIA_LAN_NETWORKS", "192.168.0.0/24,127.0.0.0/8")
        self.networks = [ipaddress.ip_network(n.strip()) for n in networks.split(",") if n.strip()]
        hosts = os.environ.get("MEDIA_LAN_HOSTS", "localhost")
        self.hosts = {h.strip().lower() for h in hosts.split(",") if h.strip()}

    def network_allowed(self, address):
        try:
            ip = ipaddress.ip_address(address)
        except ValueError:
            return False
        return any(ip in network for network in self.networks)
