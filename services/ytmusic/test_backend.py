import unittest
from client import YTMusicService, parse_duration_to_ms
from models import RemoteTrack

class TestYTMusicBackend(unittest.TestCase):

    def setUp(self):
        self.service = YTMusicService.get_instance()

    def test_duration_parsing(self):
        self.assertEqual(parse_duration_to_ms("3:45"), 225000)
        self.assertEqual(parse_duration_to_ms("1:02:03"), 3723000)
        self.assertEqual(parse_duration_to_ms("45"), 45000)
        self.assertIsNone(parse_duration_to_ms(None))
        self.assertIsNone(parse_duration_to_ms(""))

    def test_search_songs(self):
        res = self.service.search("Blinding Lights", filter_type="songs", limit=5)
        self.assertGreater(len(res.tracks), 0)
        first_track = res.tracks[0]
        self.assertIsInstance(first_track, RemoteTrack)
        self.assertTrue(bool(first_track.videoId))
        self.assertTrue(bool(first_track.title))

    def test_search_artists(self):
        res = self.service.search("Taylor Swift", filter_type="artists", limit=3)
        self.assertGreater(len(res.artists), 0)
        first_artist = res.artists[0]
        self.assertIn("Taylor", first_artist["name"])

    def test_search_albums(self):
        res = self.service.search("After Hours", filter_type="albums", limit=3)
        self.assertGreater(len(res.albums), 0)
        first_album = res.albums[0]
        self.assertTrue(bool(first_album.id))
        self.assertTrue(bool(first_album.title))

    def test_related_tracks(self):
        # Using a well-known song video ID (Blinding Lights)
        related = self.service.get_related("J7p4bzqLvCw")
        self.assertGreater(len(related), 0)
        self.assertTrue(bool(related[0].videoId))

if __name__ == "__main__":
    unittest.main()
