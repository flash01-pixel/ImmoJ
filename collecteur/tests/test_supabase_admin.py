import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "outils"))
import supabase_admin  # noqa: E402


def test_topic_stable_et_imprevisible():
    t = supabase_admin.topic_ntfy("abcdefghijklmnopqrst")
    assert t.startswith("immoj-") and len(t) == 20
    assert t == supabase_admin.topic_ntfy("abcdefghijklmnopqrst")
    assert t != supabase_admin.topic_ntfy("abcdefghijklmnopqrsu")


def test_choisir_cles_historiques():
    cles = supabase_admin.choisir_cles([
        {"name": "anon", "api_key": "eyJanon"},
        {"name": "service_role", "api_key": "eyJservice"},
    ])
    assert cles == {"anon": "eyJanon", "service": "eyJservice"}


def test_choisir_cles_nouvelles():
    cles = supabase_admin.choisir_cles([
        {"name": "default", "type": "publishable", "api_key": "sb_publishable_x"},
        {"name": "backend", "type": "secret", "api_key": "sb_secret_y"},
    ])
    assert cles == {"anon": "sb_publishable_x", "service": "sb_secret_y"}
